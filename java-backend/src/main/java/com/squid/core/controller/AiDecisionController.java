package com.squid.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squid.core.ipc.IPCMain;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP wrapper for AI decisions.
 * Bridges REST calls to the same Python decision engine used by IPCMain.
 */
@CrossOrigin
@RestController
@RequestMapping("/api/v1/ai")
public class AiDecisionController {

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/decide")
    public ResponseEntity<JsonNode> decide(@RequestBody JsonNode body) {
        try {
            // Body is the payload expected by the python-ia ipc_daemon for a 'decide' command
            String payloadJson = body.toString();
            String pythonResp = IPCMain.callPythonOnce(payloadJson);
            JsonNode parsed = mapper.readTree(pythonResp);
            return ResponseEntity.ok(parsed);
        } catch (Exception e) {
            ObjectNode err = mapper.createObjectNode();
            err.put("ok", false);
            err.put("error", "decision_failed");
            err.put("trace", e.toString());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
