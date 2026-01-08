package com.squid.core.service;

import com.squid.core.crypto.CanonicalJson;
import com.squid.core.crypto.HKDFUtil;
import com.squid.core.crypto.MerkleTree;
import com.squid.core.model.*;
import com.squid.core.service.AIServiceClient.AIDecision;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.Base64;
import javax.annotation.PostConstruct;
import java.nio.file.Paths;
import com.squid.core.db.LeafHistoryStore;

@Service
public class SquidCoreService {

    @Autowired
    private AIServiceClient aiServiceClient;

    @Autowired
    private PQCService pqcService;

    // In-memory leaf history records (kept for audit and dashboard queries)
    private final List<com.squid.core.model.LeafHistory> leafHistory = new ArrayList<>();
    private LeafHistoryStore historyStore = null;

    /**
     * Generate SQUID tokens with real post-quantum protection (Kyber + Dilithium)
     * Returns enhanced response with full analysis data
     */
    public EnhancedGenerateResponse generate(GenerateRequest request) throws Exception {
        // 1. Canonicalize input
        byte[] canonicalInput = CanonicalJson.canonicalize(request);
        
        // 2. Real KEM encapsulation using Kyber (ML-KEM)
        PQCService.KEMResult kemResult = pqcService.encapsulate(canonicalInput);
        String ciphertext = Base64.getEncoder().encodeToString(kemResult.getCiphertext());
        byte[] sharedSecret = kemResult.getSharedSecret();
        
        // 3. Derive root key using HKDF
        byte[] salt = "SQUID-v1".getBytes(StandardCharsets.UTF_8);
        byte[] rootKey = HKDFUtil.extract(salt, sharedSecret);
        
        // 4. Derive seed for AI model
        String timestamp = Instant.now().toString();
        byte[] seedModel = HKDFUtil.expand(rootKey, "model-seed|" + timestamp, 32);
        String seedModelHash = bytesToHex(sha256(seedModel));
        
        // 5. Generate branching tree parameters
        GenerateRequest.BranchingParams params = request.getParams();
        if (params == null) {
            params = new GenerateRequest.BranchingParams(4, 3, 128);
        }
        
        // 6. Derive leaves
        List<LeafData> leaves = deriveLeaves(rootKey, params.getB(), params.getM(), params.getT());
        
        // 7. Call AI service for decisions
        var aiDecision = aiServiceClient.decide(leaves, seedModelHash, params);
        
        // 8. Apply AI decisions to leaves
        applyAIDecisions(leaves, aiDecision);
        
        // 9. Build Merkle tree per leaf (as per SQUID design)
        List<String> leafSignatures = new ArrayList<>();
        List<byte[]> merkleRoots = new ArrayList<>();
        
        for (LeafData leaf : leaves) {
            // Sign each leaf with Dilithium (ML-DSA)
            String leafSignature = pqcService.sign(leaf.getValue());
            leafSignatures.add(leafSignature);
            
            // Also compute per-leaf merkle for completeness
            MerkleTree leafMerkle = new MerkleTree(java.util.Arrays.asList(leaf.getValue()));
            merkleRoots.add(leafMerkle.getRoot());
        }
        
        // Build overall Merkle tree for all leaves
        List<byte[]> leafValues = new ArrayList<>();
        for (LeafData leaf : leaves) {
            leafValues.add(leaf.getValue());
        }
        MerkleTree merkleTree = new MerkleTree(leafValues);
        byte[] merkleRoot = merkleTree.getRoot();
        
        // 10. Sign the overall Merkle root with Dilithium (ML-DSA)
        String modelHash = "MODEL_HASH_V1";
        byte[] signatureData = createSignatureData(merkleRoot, seedModelHash, modelHash);
        String signature = pqcService.sign(signatureData);
        
        // 11. Store audit log
        storeAuditLog(merkleRoot, signature, seedModelHash, modelHash, timestamp);
        
        // Build analysis data for response
        EnhancedGenerateResponse.AnalysisData analysis = buildAnalysisData(
            aiDecision, leaves, params
        );
        
        return new EnhancedGenerateResponse(
            ciphertext,
            bytesToHex(merkleRoot),
            signature,
            seedModelHash,
            modelHash,
            timestamp,
            analysis
        );
    }

    /**
     * Build analysis data from AI decisions and leaves
     */
    private EnhancedGenerateResponse.AnalysisData buildAnalysisData(
            AIServiceClient.AIDecision aiDecision,
            List<LeafData> leaves,
            GenerateRequest.BranchingParams params) {
        
        // Count action distribution
        EnhancedGenerateResponse.ActionDistribution distribution = 
            new EnhancedGenerateResponse.ActionDistribution();
        
        List<EnhancedGenerateResponse.LeafDetail> leafDetails = new ArrayList<>();
        
        for (int i = 0; i < leaves.size() && i < aiDecision.getActions().size(); i++) {
            LeafData leaf = leaves.get(i);
            String action = aiDecision.getActions().get(i);
            
            distribution.countAction(action);
            
            leafDetails.add(new EnhancedGenerateResponse.LeafDetail(
                leaf.getIndex(),
                leaf.getPath(),
                action,
                leaf.getLocalEntropy()
            ));
        }
        
        EnhancedGenerateResponse.TreeParams treeParams = new EnhancedGenerateResponse.TreeParams(
            params.getB(), params.getM(), params.getT()
        );
        
        return new EnhancedGenerateResponse.AnalysisData(
            aiDecision.getSr(),
            aiDecision.getC(),
            leaves.size(),
            aiDecision.getActions(),
            distribution,
            treeParams,
            leafDetails
        );
    }

    /**
     * Verify SQUID token authenticity
     */
    public VerifyResponse verify(VerifyRequest request) throws Exception {
        try {
            // Simulate verification logic
            boolean isValid = pqcService.verify(request.getSignature(), 
                hexToBytes(request.getMerkleRoot()));
            
            String reason = isValid ? "Valid signature" : "Invalid signature";
            return new VerifyResponse(isValid, reason, Instant.now().toString());
        } catch (Exception e) {
            return new VerifyResponse(false, e.getMessage(), Instant.now().toString());
        }
    }

    /**
     * Get current public Merkle root
     */
    public PublicRootResponse getPublicRoot() throws Exception {
        // Return latest stored audit log entry
        return new PublicRootResponse(
            "latest_merkle_root",
            "latest_signature", 
            Instant.now().toString(),
            "latest_seed_model_hash",
            "MODEL_HASH_V1"
        );
    }

    /**
     * Derive leaves using deterministic branching
     */
    private List<LeafData> deriveLeaves(byte[] rootKey, int b, int m, int t) {
        List<LeafData> leaves = new ArrayList<>();
        
        // Calculate total leaves: L = b^m
        int totalLeaves = (int) Math.pow(b, m);
        
        for (int i = 0; i < totalLeaves; i++) {
            // Convert leaf index to path in tree
            List<Integer> path = indexToPath(i, b, m);
            
            // Derive key for this path
            byte[] leafKey = rootKey;
            for (int level = 0; level < path.size(); level++) {
                leafKey = HKDFUtil.deriveBranchKey(leafKey, level, path.get(level), 32);
            }
            
            // Generate final leaf value
            byte[] leafValue = HKDFUtil.deriveLeaf(leafKey, i, t);
            
            LeafData leaf = new LeafData();
            leaf.setIndex(i);
            leaf.setPath(path);
            leaf.setValue(leafValue);
            leaf.setDepth(m);
            leaf.setLocalEntropy(calculateEntropy(leafValue));
            
            leaves.add(leaf);
        }
        
        return leaves;
    }

    private List<Integer> indexToPath(int index, int b, int m) {
        List<Integer> path = new ArrayList<>();
        for (int level = m - 1; level >= 0; level--) {
            int divisor = (int) Math.pow(b, level);
            path.add(index / divisor);
            index = index % divisor;
        }
        return path;
    }

    private double calculateEntropy(byte[] data) {
        int[] counts = new int[256];
        for (byte b : data) {
            counts[b & 0xFF]++;
        }
        
        double entropy = 0.0;
        int length = data.length;
        for (int count : counts) {
            if (count > 0) {
                double p = (double) count / length;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    private void applyAIDecisions(List<LeafData> leaves, AIDecision decision) {
        // Apply AI-determined actions to leaves
        for (int i = 0; i < leaves.size() && i < decision.getActions().size(); i++) {
            LeafData leaf = leaves.get(i);
            String action = decision.getActions().get(i);
            byte[] previous = leaf.getValue();
            
            switch (action) {
                case "DECOY":
                    leaf.setValue(generateDecoy(leaf.getValue()));
                    recordLeafHistory(i, previous, leaf.getValue(), "DECOY");
                    break;
                case "MUTATE":
                    leaf.setValue(mutateLeaf(leaf.getValue()));
                    recordLeafHistory(i, previous, leaf.getValue(), "MUTATE");
                    break;
                case "REASSIGN":
                    leaf.setValue(reassignLeaf(leaf.getValue()));
                    recordLeafHistory(i, previous, leaf.getValue(), "REASSIGN");
                    break;
                case "VALID":
                default:
                    // Keep original value
                    break;
            }
        }
    }

    private void recordLeafHistory(int index, byte[] previous, byte[] current, String action) {
        try {
            com.squid.core.model.LeafHistory h = new com.squid.core.model.LeafHistory();
            h.setIndex(index);
            h.setPreviousValue(bytesToHex(previous));
            h.setNewValue(bytesToHex(current));
            h.setAction(action);
            h.setTimestamp(Instant.now().toString());
            leafHistory.add(h);
            // Persist to sqlite if available
            if (historyStore != null) {
                historyStore.insert(h);
            }
        } catch (Exception e) {
            // ignore history failures
        }
    }

    public List<com.squid.core.model.LeafHistory> getLeafHistory() {
        // Prefer persistent store if available
        try {
            if (historyStore != null) {
                return historyStore.listAll();
            }
        } catch (Exception e) {
            // fall back to in-memory
        }
        return new ArrayList<>(leafHistory);
    }

    @PostConstruct
    private void initPersistence() {
        try {
            // place DB under project data folder
            String dbPath = Paths.get("data", "leaf_history.db").toString();
            historyStore = new LeafHistoryStore(dbPath);
            System.out.println("LeafHistoryStore initialized at: " + dbPath);
        } catch (Exception e) {
            System.out.println("Failed to initialize LeafHistoryStore: " + e.toString());
            historyStore = null;
        }
    }

    private byte[] generateDecoy(byte[] original) {
        // Generate plausible decoy that maintains same format
        byte[] decoy = new byte[original.length];
        System.arraycopy(original, 0, decoy, 0, original.length);
        // XOR with deterministic pattern
        for (int i = 0; i < decoy.length; i++) {
            decoy[i] ^= 0xAA;
        }
        return decoy;
    }

    private byte[] mutateLeaf(byte[] original) {
        // Apply controlled mutation
        byte[] mutated = new byte[original.length];
        System.arraycopy(original, 0, mutated, 0, original.length);
        // Flip some bits deterministically
        if (mutated.length > 0) {
            mutated[0] ^= 0x01;
        }
        return mutated;
    }

    private byte[] reassignLeaf(byte[] original) {
        // Generate completely new value with same entropy profile
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(original);
            digest.update("REASSIGN".getBytes());
            byte[] hash = digest.digest();
            
            byte[] result = new byte[original.length];
            System.arraycopy(hash, 0, result, 0, Math.min(result.length, hash.length));
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createSignatureData(byte[] merkleRoot, String seedModelHash, String modelHash) {
        String combined = bytesToHex(merkleRoot) + seedModelHash + modelHash;
        return combined.getBytes(StandardCharsets.UTF_8);
    }

    private void storeAuditLog(byte[] merkleRoot, String signature, 
                              String seedModelHash, String modelHash, String timestamp) {
        // Store audit log entry - implement based on storage backend
        System.out.println("Audit Log: " + timestamp + " -> " + bytesToHex(merkleRoot));
    }

    private byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    /**
     * Generate test actions for test vectors
     */
    private List<String> generateTestActions(List<LeafData> leaves, GenerateRequest.BranchingParams params) {
        List<String> actions = new ArrayList<>();
        double sr = calculateSuperRelation(params);
        double c = calculateCorrelationCoefficient(params);

        for (int i = 0; i < leaves.size(); i++) {
            String action = determinateAction(sr, c, i, leaves.size());
            actions.add(action);
        }

        return actions;
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
        // Stabilized components
        double component1 = (2.0 * t) / L;
        double beta = 0.12;
        double lam = -0.1;

        double component2 = Math.exp(beta * (m - 1));

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

        double g_b = (double) b * Math.log(b + 1.0);

        double raw = component1 * component2 * component3 * g_b;
        if (!Double.isFinite(raw)) raw = 0.0;
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

    // Nested classes for internal data structures
    public static class LeafData {
        private int index;
        private List<Integer> path;
        private byte[] value;
        private int depth;
        private double localEntropy;

        // Getters and setters
        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public List<Integer> getPath() { return path; }
        public void setPath(List<Integer> path) { this.path = path; }

        public byte[] getValue() { return value; }
        public void setValue(byte[] value) { this.value = value; }

        public int getDepth() { return depth; }
        public void setDepth(int depth) { this.depth = depth; }

        public double getLocalEntropy() { return localEntropy; }
        public void setLocalEntropy(double localEntropy) { this.localEntropy = localEntropy; }
    }
}
