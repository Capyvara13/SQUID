package com.squid.core.controller;

import com.squid.core.service.AIDecisionStateService;
import com.squid.core.service.CryptoPipelineService;
import com.squid.core.service.DynamicMerkleTreeService;
import com.squid.core.service.GlobalIntegrityTree;
import com.squid.core.service.IterativeSeedEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/v1/global")
public class GlobalIntegrityController {

    private final GlobalIntegrityTree globalTree;
    private final DynamicMerkleTreeService dynamicService;
    private final CryptoPipelineService pipeline;
    private final IterativeSeedEngine iterative;
    private final AIDecisionStateService aiState;

    public GlobalIntegrityController(GlobalIntegrityTree globalTree,
                                     DynamicMerkleTreeService dynamicService,
                                     CryptoPipelineService pipeline,
                                     IterativeSeedEngine iterative,
                                     AIDecisionStateService aiState) {
        this.globalTree = globalTree;
        this.dynamicService = dynamicService;
        this.pipeline = pipeline;
        this.iterative = iterative;
        this.aiState = aiState;
    }

    @GetMapping("/root")
    public ResponseEntity<java.util.Map<String,Object>> getGlobalRoot() {
        try {
            Map<String,Object> status = dynamicService.getTreeStatus();
            String dynamicRootHex = (String) status.get("rootHash");
            List<String> iterativeRoots = iterative.getRecentIterativeRoots();
            List<Map<String,Object>> ops = pipeline.getOperationsSnapshot();
            List<String> opRoots = new ArrayList<>();
            for (Map<String,Object> op : ops) {
                Object meta = op.get("metadata");
                if (meta instanceof Map<?,?>) {
                    Object root = ((Map<?,?>) meta).get("merkle_root");
                    if (root instanceof String) {
                        opRoots.add((String) root);
                    }
                }
            }
            String aiHash = aiState.getLastDecisionHashHex();
            String globalRoot = globalTree.computeGlobalRoot(dynamicRootHex, iterativeRoots, opRoots, aiHash);

            java.util.Map<String,Object> out = new java.util.LinkedHashMap<>();
            out.put("globalRoot", globalRoot);
            out.put("dynamicRoot", dynamicRootHex);
            out.put("iterativeRoots", iterativeRoots);
            out.put("operationRoots", opRoots);
            out.put("aiDecisionHash", aiHash);
            out.put("entropyBudget", aiState.getEntropyBudget());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
