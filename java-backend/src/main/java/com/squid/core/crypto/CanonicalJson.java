package com.squid.core.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * JSON Canonicalization implementation following RFC 8785 (JCS)
 * Ensures deterministic serialization for cryptographic operations
 */
public class CanonicalJson {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * Canonicalize a JSON object to deterministic byte representation
     */
    public static byte[] canonicalize(Object obj) throws IOException {
        JsonNode node = MAPPER.valueToTree(obj);
        String canonical = canonicalizeNode(node);
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Canonicalize a JSON string
     */
    public static byte[] canonicalize(String json) throws IOException {
        JsonNode node = MAPPER.readTree(json);
        String canonical = canonicalizeNode(node);
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
    
    private static String canonicalizeNode(JsonNode node) {
        if (node.isNull()) {
            return "null";
        } else if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        } else if (node.isNumber()) {
            return canonicalizeNumber(node);
        } else if (node.isTextual()) {
            return canonicalizeString(node.asText());
        } else if (node.isArray()) {
            return canonicalizeArray((ArrayNode) node);
        } else if (node.isObject()) {
            return canonicalizeObject((ObjectNode) node);
        } else {
            throw new IllegalArgumentException("Unsupported JSON node type");
        }
    }
    
    private static String canonicalizeNumber(JsonNode node) {
        if (node.isInt() || node.isLong()) {
            return String.valueOf(node.asLong());
        } else if (node.isFloat() || node.isDouble()) {
            double value = node.asDouble();
            if (Double.isInfinite(value) || Double.isNaN(value)) {
                throw new IllegalArgumentException("Invalid number: " + value);
            }
            
            // Use scientific notation for very large/small numbers
            if (Math.abs(value) >= 1e21 || (Math.abs(value) <= 1e-6 && value != 0)) {
                return String.format("%.15e", value).replaceAll("e\\+0+", "e").replaceAll("e-0+", "e-");
            } else {
                // Remove trailing zeros and unnecessary decimal point
                String result = String.format("%.15f", value);
                result = result.replaceAll("0+$", "");
                result = result.replaceAll("\\.$", "");
                return result;
            }
        } else {
            return node.asText();
        }
    }
    
    private static String canonicalizeString(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        
        sb.append('"');
        return sb.toString();
    }
    
    private static String canonicalizeArray(ArrayNode array) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(canonicalizeNode(array.get(i)));
        }
        
        sb.append(']');
        return sb.toString();
    }
    
    private static String canonicalizeObject(ObjectNode object) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        
        // Sort keys alphabetically
        List<String> keys = new ArrayList<>();
        Iterator<String> fieldNames = object.fieldNames();
        while (fieldNames.hasNext()) {
            keys.add(fieldNames.next());
        }
        Collections.sort(keys);
        
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            
            sb.append(canonicalizeString(key));
            sb.append(':');
            sb.append(canonicalizeNode(object.get(key)));
        }
        
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Verify that two JSON representations are canonically equivalent
     */
    public static boolean areEquivalent(String json1, String json2) {
        try {
            byte[] canonical1 = canonicalize(json1);
            byte[] canonical2 = canonicalize(json2);
            return java.util.Arrays.equals(canonical1, canonical2);
        } catch (IOException e) {
            return false;
        }
    }
}
