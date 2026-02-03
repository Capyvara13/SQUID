package com.squid.core.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squid.core.service.DynamicMerkleTreeService;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Simple IPC main to accept JSON commands on stdin and respond JSON on stdout.
 * For `decide` command this program will spawn the Python IPC daemon in
 * `--once` mode and forward the payload to it.
 *
 * Commands: {"cmd":"health"}
 *           {"cmd":"decide","payload":{...}}
 */
public class IPCMain {

    private static final ObjectMapper M;

    static {
        M = new ObjectMapper();
        // register Java Time module to support java.time types like Instant
        M.registerModule(new JavaTimeModule());
        M.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

        // Create persistent service instances to maintain state across commands
        DynamicMerkleTreeService dynamicService = null;
        try {
            dynamicService = new DynamicMerkleTreeService();
        } catch (Exception e) {
            ObjectNode resp = M.createObjectNode();
            resp.put("ok", false);
            resp.put("error", "failed_to_init_dynamic_service");
            resp.put("trace", e.toString());
            writer.write(resp.toString() + "\n");
            writer.flush();
            // continue but some commands will fail
        }

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            try {
                ObjectNode req = (ObjectNode) M.readTree(line);
                String cmd = req.has("cmd") ? req.get("cmd").asText() : "";

                if ("decide".equals(cmd)) {
                    ObjectNode payload = (ObjectNode) req.get("payload");

                    boolean autoApply = false;
                    if (req.has("auto_apply")) {
                        autoApply = req.get("auto_apply").asBoolean(false);
                    } else if (payload != null && payload.has("auto_apply")) {
                        autoApply = payload.get("auto_apply").asBoolean(false);
                    }

                    String payloadStr = payload != null ? payload.toString() : "{}";
                    String pythonResp = callPythonOnce(payloadStr);

                    // If requested, parse python response and apply rotation plan via dynamicService
                    if (autoApply) {
                        try {
                            com.fasterxml.jackson.databind.JsonNode pythonNode = M.readTree(pythonResp);

                            // Locate rotation_plan either at root or nested under result
                            com.fasterxml.jackson.databind.JsonNode rotationPlan = null;
                            if (pythonNode.has("rotation_plan")) {
                                rotationPlan = pythonNode.get("rotation_plan");
                            } else if (pythonNode.has("result") && pythonNode.get("result").has("rotation_plan")) {
                                rotationPlan = pythonNode.get("result").get("rotation_plan");
                            }

                            if (rotationPlan != null && rotationPlan.has("indices") && dynamicService != null) {
                                java.util.List<Integer> indices = new java.util.ArrayList<>();
                                for (com.fasterxml.jackson.databind.JsonNode n : rotationPlan.get("indices")) {
                                    indices.add(n.asInt());
                                }

                                java.util.Map<String,Object> rotateRes = dynamicService.rotateLeavesByIndex(indices, "ai-auto-apply");
                                // attach applied_rotation result to python response
                                ((ObjectNode) pythonNode).set("applied_rotation", M.valueToTree(rotateRes));
                                writer.write(pythonNode.toString() + "\n");
                                writer.flush();
                            } else {
                                // nothing to apply or missing service, return original python response
                                writer.write(pythonResp + "\n");
                                writer.flush();
                            }
                        } catch (Exception e) {
                            ObjectNode resp = M.createObjectNode();
                            resp.put("ok", false);
                            resp.put("error", "auto_apply_failed");
                            resp.put("trace", e.toString());
                            // include original python response for debugging
                            resp.put("python_response", pythonResp);
                            writer.write(resp.toString() + "\n");
                            writer.flush();
                        }
                    } else {
                        writer.write(pythonResp + "\n");
                        writer.flush();
                    }
                } else if ("rotate_indices".equals(cmd)) {
                    // payload: { indices: [0,1,2], reason: "..." }
                    try {
                        java.util.List<Integer> indices = new java.util.ArrayList<>();
                        if (req.has("payload") && req.get("payload").has("indices")) {
                            for (com.fasterxml.jackson.databind.JsonNode n : req.get("payload").get("indices")) {
                                indices.add(n.asInt());
                            }
                        }
                        String reason = null;
                        if (req.has("payload") && req.get("payload").has("reason")) {
                            reason = req.get("payload").get("reason").asText();
                        }

                        if (dynamicService == null) {
                            ObjectNode resp = M.createObjectNode();
                            resp.put("ok", false);
                            resp.put("error", "dynamic_service_unavailable");
                            writer.write(resp.toString() + "\n");
                            writer.flush();
                        } else {
                            java.util.Map<String,Object> res = dynamicService.rotateLeavesByIndex(indices, reason);
                            writer.write(M.writeValueAsString(res) + "\n");
                            writer.flush();
                        }
                    } catch (Exception e) {
                        ObjectNode resp = M.createObjectNode();
                        resp.put("ok", false);
                        resp.put("error", "rotate_failed");
                        resp.put("trace", e.toString());
                        writer.write(resp.toString() + "\n");
                        writer.flush();
                    }
                } else if ("get_transitions".equals(cmd)) {
                    if (dynamicService == null) {
                        ObjectNode resp = M.createObjectNode();
                        resp.put("ok", false);
                        resp.put("error", "dynamic_service_unavailable");
                        writer.write(resp.toString() + "\n");
                        writer.flush();
                    } else {
                        java.util.List<java.util.Map<String,Object>> transitions = dynamicService.getAutonomousTransitions();
                        writer.write(M.writeValueAsString(transitions) + "\n");
                        writer.flush();
                    }
                } else if ("get_audit".equals(cmd)) {
                    if (dynamicService == null) {
                        ObjectNode resp = M.createObjectNode();
                        resp.put("ok", false);
                        resp.put("error", "dynamic_service_unavailable");
                        writer.write(resp.toString() + "\n");
                        writer.flush();
                    } else {
                        java.util.Map<String,Object> audit = dynamicService.getAuditTrail();
                        writer.write(M.writeValueAsString(audit) + "\n");
                        writer.flush();
                    }
                } else if ("history".equals(cmd) || "get_leaf_history".equals(cmd)) {
                    // Return external transition history
                    if (dynamicService == null) {
                        ObjectNode resp = M.createObjectNode();
                        resp.put("ok", false);
                        resp.put("error", "dynamic_service_unavailable");
                        writer.write(resp.toString() + "\n");
                        writer.flush();
                    } else {
                        java.util.List<com.squid.core.model.MerkleTreeTransitionEvent> hist = dynamicService.getTransitionHistory();
                        writer.write(M.writeValueAsString(hist) + "\n");
                        writer.flush();
                    }
                } else if ("health".equals(cmd)) {
                    ObjectNode resp = M.createObjectNode();
                    resp.put("ok", true);
                    resp.put("service", "java-ipc");
                    writer.write(resp.toString() + "\n");
                    writer.flush();
                } else if ("generate".equals(cmd)) {
                    // handle generate command: create leaves deterministically from seed
                    try {
                        if (dynamicService == null) {
                            ObjectNode resp = M.createObjectNode();
                            resp.put("ok", false);
                            resp.put("error", "dynamic_service_unavailable");
                            writer.write(resp.toString() + "\n");
                            writer.flush();
                        } else {
                            com.fasterxml.jackson.databind.JsonNode payload = req.get("payload");
                            String seed = payload != null && payload.has("seed") ? payload.get("seed").asText() : "seed";
                            int b = 4, m = 3;
                            if (payload != null && payload.has("params")) {
                                com.fasterxml.jackson.databind.JsonNode p = payload.get("params");
                                if (p.has("b")) b = p.get("b").asInt();
                                if (p.has("m")) m = p.get("m").asInt();
                            }

                            // limit leaves to avoid excessive sizes
                            int totalLeaves = 1;
                            try { totalLeaves = (int) Math.pow(b, m); } catch (Exception ignored) {}
                            totalLeaves = Math.max(1, Math.min(totalLeaves, 256));

                            java.util.List<String> newLeaves = new java.util.ArrayList<>();
                            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                            for (int i = 0; i < totalLeaves; i++) {
                                md.reset();
                                md.update(seed.getBytes(StandardCharsets.UTF_8));
                                md.update((byte) ':');
                                md.update(Integer.toString(i).getBytes(StandardCharsets.UTF_8));
                                byte[] h = md.digest();
                                // convert to hex string to serve as leaf data
                                StringBuilder sb = new StringBuilder();
                                for (byte bb : h) sb.append(String.format("%02x", bb));
                                newLeaves.add(sb.toString());
                            }

                            java.util.Map<String,Object> res = dynamicService.addLeaves(newLeaves, "generate_from_seed");
                            writer.write(M.writeValueAsString(res) + "\n");
                            writer.flush();
                        }
                    } catch (Exception e) {
                        ObjectNode resp = M.createObjectNode();
                        resp.put("ok", false);
                        resp.put("error", "generate_failed");
                        resp.put("trace", e.toString());
                        writer.write(resp.toString() + "\n");
                        writer.flush();
                    }
                } else {
                    ObjectNode resp = M.createObjectNode();
                    resp.put("ok", false);
                    resp.put("error", "unknown_cmd");
                    writer.write(resp.toString() + "\n");
                    writer.flush();
                }
            } catch (Exception e) {
                ObjectNode resp = M.createObjectNode();
                resp.put("ok", false);
                resp.put("error", "invalid_request");
                resp.put("trace", e.toString());
                writer.write(resp.toString() + "\n");
                writer.flush();
            }
        }
    }

    public static String callPythonOnce(String payloadJson) throws IOException, InterruptedException {
        // Allow the launcher to specify a Python interpreter via environment variable
        // e.g. SQUID_PYTHON=C:\Python39\python.exe. If provided, try it first.
        String envPython = System.getenv("SQUID_PYTHON");

        // Try several python invocations to ensure Python 3 is used (avoid Python 2 on some systems)
        // If envPython is provided and points to the 'py' launcher, prefer 'py -3'.
        String[][] candidates = new String[][]{
            // If envPython provided, try it first (handle 'py' specially)
            (envPython != null ? (envPython.toLowerCase().endsWith("\\py") || envPython.toLowerCase().endsWith("\\py.exe") || envPython.toLowerCase().endsWith("/py") || envPython.toLowerCase().endsWith("/py.exe") ? new String[]{envPython, "-3", "python-ia/ipc_daemon.py", "--once"} : new String[]{envPython, "python-ia/ipc_daemon.py", "--once"}) : null),
            new String[]{"python3", "python-ia/ipc_daemon.py", "--once"},
            new String[]{"py", "-3", "python-ia/ipc_daemon.py", "--once"},
            new String[]{"python", "python-ia/ipc_daemon.py", "--once"}
        };

        Process proc = null;
        IOException lastEx = null;
        String[] usedCmd = null;
        for (String[] cmd : candidates) {
            if (cmd == null) continue;
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                proc = pb.start();
                usedCmd = cmd;
                break;
            } catch (IOException e) {
                lastEx = e;
                // try next candidate
            }
        }

        if (proc == null) {
            ObjectNode err = M.createObjectNode();
            err.put("ok", false);
            err.put("error", "failed_to_start_python");
            err.put("trace", (lastEx != null) ? lastEx.toString() : "no_interpreter_found");
            return err.toString();
        }

        // write payload JSON to python stdin
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8))) {
            // Wrap as message {"cmd":"decide","payload": ... }
            ObjectNode wrapper = M.createObjectNode();
            wrapper.put("cmd", "decide");
            wrapper.set("payload", M.readTree(payloadJson));
            bw.write(wrapper.toString());
            bw.flush();
        }

        // read python stdout
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
        }

        int exit = proc.waitFor();
        String result = out.toString().trim();
        if (result.isEmpty()) {
            ObjectNode err = M.createObjectNode();
            err.put("ok", false);
            err.put("error", "no_response_from_python");
            err.put("python_cmd", usedCmd != null ? String.join(" ", usedCmd) : "");
            err.put("python_exit", exit);
            return err.toString();
        }

        // If the python output is not valid JSON, return structured error including raw output
        try {
            M.readTree(result);
            return result;
        } catch (Exception e) {
            ObjectNode err = M.createObjectNode();
            err.put("ok", false);
            err.put("error", "invalid_python_response");
            err.put("python_cmd", usedCmd != null ? String.join(" ", usedCmd) : "");
            err.put("python_exit", exit);
            err.put("python_output", result);
            return err.toString();
        }
    }
}
