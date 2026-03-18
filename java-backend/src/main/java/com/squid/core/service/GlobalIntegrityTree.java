package com.squid.core.service;

import com.squid.core.crypto.MerkleTree;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GlobalIntegrityTree {

    private final DynamicMerkleTreeService dynamicService;

    public GlobalIntegrityTree(DynamicMerkleTreeService dynamicService) {
        this.dynamicService = dynamicService;
    }

    public String computeGlobalRoot(String dynamicRootHex,
                                    List<String> iterativeRootsHex,
                                    List<String> operationRootsHex,
                                    String aiDecisionHashHex) {
        List<byte[]> leaves = new ArrayList<>();
        if (dynamicRootHex != null && !dynamicRootHex.isEmpty()) {
            leaves.add(hexToBytes(dynamicRootHex));
        }
        if (iterativeRootsHex != null) {
            for (String h : iterativeRootsHex) {
                if (h != null && !h.isEmpty()) {
                    leaves.add(hexToBytes(h));
                }
            }
        }
        if (operationRootsHex != null) {
            for (String h : operationRootsHex) {
                if (h != null && !h.isEmpty()) {
                    leaves.add(hexToBytes(h));
                }
            }
        }
        if (aiDecisionHashHex != null && !aiDecisionHashHex.isEmpty()) {
            leaves.add(hexToBytes(aiDecisionHashHex));
        }
        if (leaves.isEmpty()) {
            leaves.add("GLOBAL_EMPTY".getBytes(StandardCharsets.UTF_8));
        }
        MerkleTree mt = new MerkleTree(leaves);
        return bytesToHex(mt.getRoot());
    }

    public String computeFromSystemSnapshot(List<String> iterativeRootsHex,
                                            List<String> operationRootsHex,
                                            String aiDecisionHashHex) {
        Map<String, Object> status = dynamicService.getTreeStatus();
        String dynamicRootHex = (String) status.get("rootHash");
        return computeGlobalRoot(dynamicRootHex, iterativeRootsHex, operationRootsHex, aiDecisionHashHex);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
