package com.squid.core.service;

import com.squid.core.model.GenerateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIServiceClient {

    @Value("${python.ia.url:http://python-ia:5000}")
    private String pythonIaUrl;

    private final WebClient webClient;

    public AIServiceClient() {
        this.webClient = WebClient.builder().build();
    }

    /**
     * Call Python AI service to get decisions for leaves
     */
    public AIDecision decide(List<SquidCoreService.LeafData> leaves, String seedModelHash, 
                           GenerateRequest.BranchingParams params) {
        try {
            // Prepare features for AI service
            Map<String, Object> request = new HashMap<>();
            request.put("seed_model_hash", seedModelHash);
            request.put("params", params);
            request.put("features", extractFeatures(leaves, params));

            // Call Python service
            Map<String, Object> response = webClient.post()
                .uri(pythonIaUrl + "/decide")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return parseAIResponse(response);
        } catch (Exception e) {
            // Fallback to deterministic policy if AI service unavailable
            return deterministicFallback(leaves, params);
        }
    }

    private List<Map<String, Object>> extractFeatures(List<SquidCoreService.LeafData> leaves, 
                                                     GenerateRequest.BranchingParams params) {
        List<Map<String, Object>> features = new ArrayList<>();
        
        long timestamp = System.currentTimeMillis();
        int totalLeaves = leaves.size();
        
        for (SquidCoreService.LeafData leaf : leaves) {
            Map<String, Object> feature = new HashMap<>();
            feature.put("depth", leaf.getDepth());
            feature.put("index", leaf.getIndex());
            feature.put("index_hash", leaf.getIndex() % 1000); // Simplified hash
            feature.put("local_entropy", leaf.getLocalEntropy());
            feature.put("timestamp", timestamp);
            feature.put("global_L", totalLeaves);
            feature.put("global_b", params.getB());
            feature.put("global_m", params.getM());
            feature.put("global_t", params.getT());
            feature.put("last_access_count", 0); // Default for new leaves
            feature.put("leaf_hist_score", calculateHistScore(leaf));
            
            features.add(feature);
        }
        
        return features;
    }

    private double calculateHistScore(SquidCoreService.LeafData leaf) {
        // Simplified historical score based on leaf properties
        return leaf.getLocalEntropy() * 0.5 + (leaf.getIndex() % 10) * 0.1;
    }

    private AIDecision parseAIResponse(Map<String, Object> response) {
        AIDecision decision = new AIDecision();
        
        if (response != null) {
            Object srVal = response.getOrDefault("sr", 1.0);
            Object cVal = response.getOrDefault("c", 10.0);
            decision.setSr(srVal instanceof Number ? ((Number) srVal).doubleValue() : 1.0);
            decision.setC(cVal instanceof Number ? ((Number) cVal).doubleValue() : 10.0);

            decision.setActions((List<String>) response.getOrDefault("actions", new ArrayList<>()));
            decision.setModelHash((String) response.getOrDefault("model_hash", "MODEL_HASH_V1"));

            Object confVal = response.get("confidence");
            if (confVal instanceof Number) {
                decision.setDecisionConfidence(((Number) confVal).doubleValue());
            }
            Object decisionStr = response.get("decision");
            if (decisionStr instanceof String) {
                decision.setDecision((String) decisionStr);
            }
            Object driversVal = response.get("drivers");
            if (driversVal instanceof List) {
                decision.setDrivers((List<String>) driversVal);
            }
            Object eb = response.get("entropy_budget_remaining");
            if (eb instanceof Number) {
                decision.setEntropyBudgetRemaining(((Number) eb).doubleValue());
            }
        }
        
        return decision;
    }

    private AIDecision deterministicFallback(List<SquidCoreService.LeafData> leaves, 
                                           GenerateRequest.BranchingParams params) {
        AIDecision decision = new AIDecision();
        
        // Calculate SR and C using deterministic formulas
        double sr = calculateSuperRelation(params);
        double c = calculateCorrelationCoefficient(params);
        
        decision.setSr(sr);
        decision.setC(c);
        decision.setModelHash("DETERMINISTIC_V1");
        decision.setDecision("HOLD_STATE");
        decision.setDecisionConfidence(1.0);
        decision.setDrivers(java.util.Collections.singletonList("deterministic_fallback"));
        decision.setEntropyBudgetRemaining(0.0);
        
        // Apply deterministic policy
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i++) {
            String action = determinateAction(sr, c, i, leaves.size());
            actions.add(action);
        }
        decision.setActions(actions);
        
        return decision;
    }

    /**
     * Calculate Super-Relation (SR) according to specification
     * SR = (2T/L) * K^(M-1)/2 * (∑[p=1 to P_max] max(3/2)^p / (p^α * P(1-P))) * g(b)
     * Falls back to simplified formula if unstable
     */
    private double calculateSuperRelation(GenerateRequest.BranchingParams params) {
        int b = params.getB();
        int m = params.getM();
        int t = params.getT();

        // Validate inputs
        if (b <= 0 || m <= 0 || t <= 0) {
            return 1.0;
        }

        // Try full formula first
        double sr = calculateFullSuperRelation(b, m, t);
        
        // Check for instability (too large, inf, nan)
        if (Double.isInfinite(sr) || Double.isNaN(sr) || sr < 0.0 || sr > 1e6) {
            // Fall back to simplified stable formula
            sr = calculateSimplifiedSuperRelation(b, m, t);
        }
        
        // Final validation
        if (Double.isInfinite(sr) || Double.isNaN(sr) || sr < 0.0) {
            return 1.0;
        }
        
        return sr;
    }
    
    /**
     * Full SR formula with all components
     */
    private double calculateFullSuperRelation(int b, int m, int t) {
        // Calculate L = b^m
        double L = Math.pow(b, m);
        if (L <= 0) {
            return 1.0;
        }
        // Calculate components with stabilized terms
        double component1 = (2.0 * t) / L;
        double beta = 0.12; // stability param
        double lam = -0.1;  // stability param for summation

        double component2 = Math.exp(beta * (m - 1));

        // Sum component
        double component3 = 0.0;
        int P_max = 4;
        double alpha = 1.5;
        double eps = 1e-12;
        for (int p = 1; p <= P_max; p++) {
            double P = p / (double) P_max;
            double probTerm = P * (1.0 - P) + eps;
            double expTerm = Math.exp(lam * p);
            component3 += expTerm / (Math.pow(p, alpha) * probTerm);
        }

        // g(b) function: less explosive
        double g_b = (double) b * Math.log(b + 1.0);

        double raw = component1 * component2 * component3 * g_b;

        // Normalize to [0,1] via sigmoid(log1p(raw))
        if (!Double.isFinite(raw)) {
            raw = 0.0;
        }
        double normInput = Math.log1p(Math.abs(raw)) * (raw >= 0 ? 1.0 : -1.0);
        double s = 1.0;
        double normalized = 1.0 / (1.0 + Math.exp(-s * (normInput - 0.0)));
        return Math.max(0.0, Math.min(1.0, normalized));
    }
    
    /**
     * Simplified stable SR formula as fallback
     * SR_simple = (t/b^m) * (1 + b) / (1 + m)
     */
    private double calculateSimplifiedSuperRelation(int b, int m, int t) {
        try {
            double L = Math.pow(b, m);
            if (L <= 0) {
                return 1.0;
            }
            double sr = (t / L) * (1.0 + b) / (1.0 + m);
            if (Double.isInfinite(sr) || Double.isNaN(sr) || sr < 0.0) {
                return 1.0;
            }
            return sr;
        } catch (Exception e) {
            return 1.0;
        }
    }

    /**
     * Calculate Correlation Coefficient (C) according to specification
     * C = (t * b^a * ∑[i=1 to m] b_i) / (P^(2d+1))
     */
    private double calculateCorrelationCoefficient(GenerateRequest.BranchingParams params) {
        int b = params.getB();
        int m = params.getM();
        int t = params.getT();
        double a = 0.5;
        double d = m;
        double P = 0.1;

        double eps = 1e-12;
        double numerator = (double) t * Math.pow(b, a) * (b * m);
        double denom = Math.max(eps, Math.pow(P, 2 * d + 1));
        double raw = numerator / denom;

        if (!Double.isFinite(raw)) raw = 0.0;
        double normInput = Math.log1p(Math.abs(raw)) * (raw >= 0 ? 1.0 : -1.0);
        double s = 1.0;
        double normalized = 1.0 / (1.0 + Math.exp(-s * (normInput - 0.0)));
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    private String determinateAction(double sr, double c, int index, int totalLeaves) {
        double sr_min = 1.0;
        double gamma_t = 10.0;
        
        // Use seed-based deterministic randomization instead of position-based
        // This creates more varied patterns while remaining deterministic
        long seed = hashCode() ^ (index * 73856093L) ^ (Double.doubleToLongBits(sr) * 19349663L);
        java.util.Random random = new java.util.Random(seed);
        
        double decoyRate;
        if (sr >= sr_min && c >= gamma_t) {
            // High confidence zone
            decoyRate = Math.min(0.5, Math.max(0.2, sr / 10.0));
        } else {
            // Low confidence zone
            decoyRate = Math.min(0.1, Math.max(0.01, c / 100.0));
        }
        
        // Use randomization instead of position to decide action
        double rand = random.nextDouble();
        
        if (rand < decoyRate) {
            return sr >= sr_min ? "DECOY" : "MUTATE";
        } else if (rand < decoyRate + 0.05) {
            return sr >= sr_min ? "MUTATE" : "REASSIGN";
        }
        
        return "VALID";
    }

    /**
     * AI Decision response structure
     */
        public static class AIDecision {
        private double sr;
        private double c;
        private List<String> actions;
        private String modelHash;

        // Explainability fields
        private String decision;
        private double decisionConfidence;
        private java.util.List<String> drivers;
        private double entropyBudgetRemaining;

        // Getters and setters
        public double getSr() { return sr; }
        public void setSr(double sr) { this.sr = sr; }

        public double getC() { return c; }
        public void setC(double c) { this.c = c; }

        public List<String> getActions() { return actions; }
        public void setActions(List<String> actions) { this.actions = actions; }

        public String getModelHash() { return modelHash; }
        public void setModelHash(String modelHash) { this.modelHash = modelHash; }

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }

        public double getDecisionConfidence() { return decisionConfidence; }
        public void setDecisionConfidence(double decisionConfidence) { this.decisionConfidence = decisionConfidence; }

        public java.util.List<String> getDrivers() { return drivers; }
        public void setDrivers(java.util.List<String> drivers) { this.drivers = drivers; }

        public double getEntropyBudgetRemaining() { return entropyBudgetRemaining; }
        public void setEntropyBudgetRemaining(double entropyBudgetRemaining) { this.entropyBudgetRemaining = entropyBudgetRemaining; }
    }
}
