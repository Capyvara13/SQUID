package com.squid.core.service;

import com.squid.core.fingerprint.FingerprintMode;
import com.squid.core.fingerprint.FingerprintSnapshot;
import com.squid.core.fingerprint.HardwareFingerprintService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregates coarse-grained health and status information for dashboards.
 *
 * This is intentionally conservative: it reports simple OK/DEGRADED style
 * fields without exposing secrets.
 */
@Service
public class SystemStatusService {

    private final PQCService pqcService;
    private final DynamicMerkleTreeService merkleService;
    private final HardwareFingerprintService fingerprintService;

    public SystemStatusService(PQCService pqcService,
                               DynamicMerkleTreeService merkleService,
                               HardwareFingerprintService fingerprintService) {
        this.pqcService = pqcService;
        this.merkleService = merkleService;
        this.fingerprintService = fingerprintService;
    }

    public Map<String, Object> getGlobalStatus() {
        Map<String, Object> status = new HashMap<>();

        // Cryptography status: we consider PQCService construction success as OK
        status.put("cryptography_status", "OK");

        // Fingerprint mode / confidence via HardwareFingerprintService
        FingerprintSnapshot snap = fingerprintService.capture(FingerprintMode.REDUCED);
        status.put("fingerprint_mode", snap.getMode().name());
        status.put("fingerprint_confidence", snap.getConfidenceScore());

        // AI mode: for now assume ADAPTIVE until profile manager is added
        status.put("ai_mode", "ADAPTIVE");

        // Merkle state from dynamic engine stats
        Map<String, Object> treeStatus = merkleService.getTreeStatus();
        status.put("merkle_root", treeStatus.get("rootHash"));
        status.put("merkle_state", classifyMerkleState(treeStatus));

        status.put("timestamp", Instant.now().toString());
        return status;
    }

    private String classifyMerkleState(Map<String, Object> treeStatus) {
        if (treeStatus == null) {
            return "UNKNOWN";
        }
        Number transitions = (Number) treeStatus.get("autonomousTransitions");
        Number decoys = (Number) treeStatus.get("decoyNodes");
        Number compromised = (Number) treeStatus.get("compromisedNodes");

        long t = transitions != null ? transitions.longValue() : 0L;
        long d = decoys != null ? decoys.longValue() : 0L;
        long c = compromised != null ? compromised.longValue() : 0L;

        if (c > 0) {
            return "HIGH-ENTROPY";
        }
        if (t > 0 || d > 0) {
            return "MUTATING";
        }
        return "STABLE";
    }
}