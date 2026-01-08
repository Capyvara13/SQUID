package com.squid.core.service;

import com.squid.core.model.ModelHistoryEntry;
import com.squid.core.model.ModelMetadata;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing model versions, history, and switching
 */
@Service
public class ModelManagementService {
    
    private final List<ModelMetadata> modelRegistry = new CopyOnWriteArrayList<>();
    private final List<ModelHistoryEntry> history = new CopyOnWriteArrayList<>();
    private ModelMetadata activeModel;

    public ModelManagementService() {
        // Initialize with default model
        initializeDefaultModel();
    }

    private void initializeDefaultModel() {
        ModelMetadata defaultModel = new ModelMetadata();
        defaultModel.setVersion("1.0.0");
        defaultModel.setTrainedAt(Instant.now());
        defaultModel.setArchitecture("PyTorch[13->128->64->4]");
        defaultModel.setActive(true);
        defaultModel.setDescription("Default SQUID AI Model");
        
        // Add dummy metrics
        defaultModel.addMetric("loss", 0.15);
        defaultModel.addMetric("accuracy", 0.94);
        defaultModel.addMetric("f1_score", 0.93);
        
        try {
            defaultModel.setHash(generateModelHash("squid-model-v1.0.0"));
        } catch (NoSuchAlgorithmException e) {
            defaultModel.setHash("default-hash");
        }
        
        this.modelRegistry.add(defaultModel);
        this.activeModel = defaultModel;
        
        // Log initialization
        ModelHistoryEntry entry = new ModelHistoryEntry(
            defaultModel.getVersion(),
            "INITIALIZE",
            "System initialization"
        );
        entry.setInitiator("SYSTEM");
        this.history.add(entry);
    }

    /**
     * Get the currently active model
     */
    public ModelMetadata getActiveModel() {
        return activeModel;
    }

    /**
     * List all registered models
     */
    public List<ModelMetadata> listModels() {
        return new ArrayList<>(modelRegistry);
    }

    /**
     * Get a model by version
     */
    public Optional<ModelMetadata> getModelByVersion(String version) {
        return modelRegistry.stream()
            .filter(m -> m.getVersion().equals(version))
            .findFirst();
    }

    /**
     * Register a new model
     */
    public ModelMetadata registerModel(String version, String architecture, 
                                       String description, Map<String, Double> metrics) 
            throws NoSuchAlgorithmException {
        
        // Check if version already exists
        if (modelRegistry.stream().anyMatch(m -> m.getVersion().equals(version))) {
            throw new IllegalArgumentException("Model version " + version + " already exists");
        }

        ModelMetadata metadata = new ModelMetadata();
        metadata.setVersion(version);
        metadata.setTrainedAt(Instant.now());
        metadata.setArchitecture(architecture);
        metadata.setDescription(description);
        metadata.setHash(generateModelHash(version));
        
        if (metrics != null) {
            metadata.setMetrics(new HashMap<>(metrics));
        }

        modelRegistry.add(metadata);

        // Log registration
        ModelHistoryEntry entry = new ModelHistoryEntry(
            version,
            "REGISTER",
            "New model registered"
        );
        entry.setDetails(architecture);
        history.add(entry);

        return metadata;
    }

    /**
     * Switch active model to a different version
     */
    public ModelMetadata switchModel(String targetVersion, String reason, String initiator) 
            throws NoSuchAlgorithmException {
        
        Optional<ModelMetadata> target = getModelByVersion(targetVersion);
        if (!target.isPresent()) {
            throw new IllegalArgumentException("Model version " + targetVersion + " not found");
        }

        // Record old model
        String oldVersion = activeModel != null ? activeModel.getVersion() : "NONE";

        // Switch
        if (activeModel != null) {
            activeModel.setActive(false);
        }
        activeModel = target.get();
        activeModel.setActive(true);

        // Log transition
        ModelHistoryEntry entry = new ModelHistoryEntry(
            targetVersion,
            "SWITCH",
            reason != null ? reason : "Manual model switch"
        );
        entry.setInitiator(initiator != null ? initiator : "UNKNOWN");
        entry.setDetails("Switched from " + oldVersion + " to " + targetVersion);
        history.add(entry);

        return activeModel;
    }

    /**
     * Get model history
     */
    public List<ModelHistoryEntry> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Get model history filtered by version
     */
    public List<ModelHistoryEntry> getHistoryByVersion(String version) {
        return history.stream()
            .filter(e -> e.getModelVersion().equals(version))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Update model metrics
     */
    public void updateModelMetrics(String version, Map<String, Double> metrics) {
        Optional<ModelMetadata> model = getModelByVersion(version);
        if (model.isPresent()) {
            model.get().setMetrics(new HashMap<>(metrics));
            
            ModelHistoryEntry entry = new ModelHistoryEntry(version, "UPDATE_METRICS", "Metrics updated");
            entry.setDetails(metrics.toString());
            history.add(entry);
        }
    }

    /**
     * Get full model statistics
     */
    public Map<String, Object> getModelStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalModels", modelRegistry.size());
        stats.put("activeModel", activeModel != null ? activeModel.getVersion() : null);
        stats.put("activeModelHash", activeModel != null ? activeModel.getHash() : null);
        stats.put("models", listModels());
        stats.put("historySize", history.size());
        stats.put("lastUpdate", !history.isEmpty() ? history.get(history.size() - 1).getTimestamp() : null);
        return stats;
    }

    /**
     * Generate a hash for a model based on version and content
     */
    private String generateModelHash(String content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
