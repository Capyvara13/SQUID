#!/usr/bin/env python3
"""
Test Vectors Generator and Validator for SQUID Python service
Generates deterministic test vectors that must match Java implementation
"""

import json
import hashlib
import numpy as np
from typing import Dict, List, Any
import logging

from squid_formulas import SuperRelationCalculator, CorrelationCalculator, PolicyCalculator
from squid_model import SquidModel
from squid_utils import set_deterministic_seed, canonicalize_json, bytes_to_hex

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TestVectorGenerator:
    """Generate and validate SQUID test vectors"""
    
    def __init__(self):
        self.sr_calc = SuperRelationCalculator()
        self.c_calc = CorrelationCalculator()
        self.policy_calc = PolicyCalculator()
        self.model = SquidModel(deterministic=True)
    
    def generate_test_vector(self, vector_id: str, params: Dict[str, int], 
                           seed: str) -> Dict[str, Any]:
        """Generate a single deterministic test vector"""
        logger.info(f"Generating test vector {vector_id} with params {params}")
        
        # Set deterministic seed
        set_deterministic_seed(seed)
        
        # Generate canonical input
        input_data = self._create_test_input(vector_id, params)
        canonical_input = canonicalize_json(input_data)
        
        # Calculate root key (simulate Java HKDF)
        root_key = self._simulate_hkdf_extract(b"TEST", canonical_input.encode())
        
        # Generate features for leaves
        features = self._generate_test_features(params, seed)
        
        # Calculate SR and C
        sr = self.sr_calc.calculate(params)
        c = self.c_calc.calculate(params)
        
        # Generate actions using model
        actions = self.model.predict_actions(features, sr, c)
        
        # Simulate leaf generation and Merkle root
        leaves = self._generate_test_leaves(root_key, params)
        merkle_root = self._calculate_merkle_root(leaves)
        
        # Create test vector
        test_vector = {
            "id": vector_id,
            "input": canonical_input,
            "root_key_hex": bytes_to_hex(root_key),
            "params": params,
            "sr": sr,
            "c": c,
            "features": features[:5],  # Include first 5 features for verification
            "actions": actions,
            "leaves": [bytes_to_hex(leaf) for leaf in leaves[:10]],  # First 10 leaves
            "merkle_root_hex": bytes_to_hex(merkle_root),
            "model_hash": self.model.get_model_hash(),
            "seed": seed
        }
        
        return test_vector
    
    def _create_test_input(self, vector_id: str, params: Dict[str, int]) -> Dict[str, Any]:
        """Create test input structure matching Java format"""
        return {
            "version": "1q",
            "ts": "2025-01-01T00:00:00Z",
            "payload": f"test_payload_{vector_id}",
            "meta": {
                "test_type": "deterministic",
                "vector_id": vector_id
            },
            "params": params
        }
    
    def _simulate_hkdf_extract(self, salt: bytes, ikm: bytes) -> bytes:
        """Simulate HKDF Extract step to match Java implementation"""
        import hmac
        
        if not salt:
            salt = b'\x00' * 32  # Zero salt for SHA-256
        
        return hmac.new(salt, ikm, hashlib.sha256).digest()
    
    def _generate_test_features(self, params: Dict[str, int], seed: str) -> List[Dict[str, Any]]:
        """Generate deterministic test features"""
        b, m, t = params['b'], params['m'], params['t']
        total_leaves = b ** m
        
        # Use seed for deterministic generation
        hash_obj = hashlib.sha256(seed.encode())
        seed_bytes = hash_obj.digest()
        np.random.seed(int.from_bytes(seed_bytes[:4], 'big') % (2**32))
        
        features = []
        timestamp = 1640995200000  # Fixed timestamp
        
        for i in range(total_leaves):
            feature = {
                "depth": m,
                "index": i,
                "index_hash": i % 1000,
                "local_entropy": 6.0 + np.random.random() * 2.0,  # 6-8 range
                "timestamp": timestamp,
                "global_L": total_leaves,
                "global_b": b,
                "global_m": m,
                "global_t": t,
                "last_access_count": np.random.randint(0, 5),
                "leaf_hist_score": np.random.random()
            }
            features.append(feature)
        
        return features
    
    def _generate_test_leaves(self, root_key: bytes, params: Dict[str, int]) -> List[bytes]:
        """Generate test leaves using deterministic derivation"""
        b, m, t = params['b'], params['m'], params['t']
        total_leaves = b ** m
        leaf_bytes = (t + 7) // 8  # Round up to nearest byte
        
        leaves = []
        for i in range(total_leaves):
            # Simulate HKDF-based leaf derivation
            leaf_data = self._derive_leaf(root_key, i, leaf_bytes)
            leaves.append(leaf_data)
        
        return leaves
    
    def _derive_leaf(self, key: bytes, index: int, length: int) -> bytes:
        """Derive leaf using HMAC (simulating Java implementation)"""
        import hmac
        
        info = f"leaf|{index}".encode()
        leaf_key = hmac.new(key, info, hashlib.sha256).digest()
        
        # Generate leaf value
        leaf_hmac = hmac.new(leaf_key, info, hashlib.sha256)
        leaf_value = leaf_hmac.digest()[:length]
        
        return leaf_value
    
    def _calculate_merkle_root(self, leaves: List[bytes]) -> bytes:
        """Calculate Merkle root using BLAKE2b (matching Java implementation)"""
        from hashlib import blake2b
        
        if not leaves:
            return b'\x00' * 32
        
        # Start with leaf hashes
        current_level = [blake2b(leaf, digest_size=32).digest() for leaf in leaves]
        
        # Build tree bottom-up
        while len(current_level) > 1:
            next_level = []
            
            for i in range(0, len(current_level), 2):
                left = current_level[i]
                right = current_level[i + 1] if i + 1 < len(current_level) else left
                
                # Hash pair
                combined = blake2b(digest_size=32)
                combined.update(left)
                combined.update(right)
                next_level.append(combined.digest())
            
            current_level = next_level
        
        return current_level[0]
    
    def generate_all_test_vectors(self) -> List[Dict[str, Any]]:
        """Generate all standard test vectors"""
        vectors = []
        
        # Test Vector 1: Basic parameters
        tv1 = self.generate_test_vector(
            "tv1", 
            {"b": 4, "m": 3, "t": 128}, 
            "test_seed_1"
        )
        vectors.append(tv1)
        
        # Test Vector 2: Different branching
        tv2 = self.generate_test_vector(
            "tv2", 
            {"b": 2, "m": 4, "t": 256}, 
            "test_seed_2"
        )
        vectors.append(tv2)
        
        # Test Vector 3: Edge case
        tv3 = self.generate_test_vector(
            "tv3", 
            {"b": 8, "m": 2, "t": 64}, 
            "test_seed_3"
        )
        vectors.append(tv3)
        
        return vectors
    
    def validate_test_vector(self, vector: Dict[str, Any]) -> bool:
        """Validate that a test vector is internally consistent"""
        try:
            # Check required fields
            required_fields = ["id", "params", "sr", "c", "actions", "merkle_root_hex"]
            for field in required_fields:
                if field not in vector:
                    logger.error(f"Missing field: {field}")
                    return False
            
            # Validate parameters
            params = vector["params"]
            if not all(key in params for key in ["b", "m", "t"]):
                logger.error("Invalid params structure")
                return False
            
            # Validate SR and C are positive
            if vector["sr"] < 0 or vector["c"] < 0:
                logger.error("SR and C must be non-negative")
                return False
            
            # Validate actions are valid
            valid_actions = {"VALID", "DECOY", "MUTATE", "REASSIGN"}
            for action in vector["actions"]:
                if action not in valid_actions:
                    logger.error(f"Invalid action: {action}")
                    return False
            
            # Validate hex strings
            try:
                bytes.fromhex(vector["merkle_root_hex"])
                if "root_key_hex" in vector:
                    bytes.fromhex(vector["root_key_hex"])
            except ValueError:
                logger.error("Invalid hex encoding")
                return False
            
            logger.info(f"Test vector {vector['id']} validation passed")
            return True
            
        except Exception as e:
            logger.error(f"Test vector validation failed: {e}")
            return False
    
    def compare_with_java(self, python_vector: Dict[str, Any], 
                         java_vector: Dict[str, Any]) -> bool:
        """Compare Python test vector with Java equivalent"""
        try:
            # Check key fields match
            comparison_fields = ["id", "params", "merkle_root_hex"]
            
            for field in comparison_fields:
                if python_vector.get(field) != java_vector.get(field):
                    logger.error(f"Mismatch in {field}: "
                               f"Python={python_vector.get(field)}, "
                               f"Java={java_vector.get(field)}")
                    return False
            
            # Compare numerical values with tolerance
            tolerance = 1e-6
            for field in ["sr", "c"]:
                py_val = python_vector.get(field, 0)
                java_val = java_vector.get(field, 0)
                if abs(py_val - java_val) > tolerance:
                    logger.error(f"Numerical mismatch in {field}: "
                               f"Python={py_val}, Java={java_val}")
                    return False
            
            logger.info(f"Test vector {python_vector['id']} matches Java implementation")
            return True
            
        except Exception as e:
            logger.error(f"Comparison failed: {e}")
            return False


def main():
    """Generate and save test vectors"""
    logger.info("Generating SQUID test vectors...")
    
    generator = TestVectorGenerator()
    vectors = generator.generate_all_test_vectors()
    
    # Validate all vectors
    all_valid = True
    for vector in vectors:
        if not generator.validate_test_vector(vector):
            all_valid = False
    
    if not all_valid:
        logger.error("Some test vectors failed validation")
        return 1
    
    # Save to file
    output_file = "/app/test-vectors/python_test_vectors.json"
    try:
        import os
        os.makedirs(os.path.dirname(output_file), exist_ok=True)
        
        with open(output_file, 'w') as f:
            json.dump({
                "test_vectors": vectors,
                "generator": "SQUID Python IA Service",
                "version": "1.0",
                "deterministic": True
            }, f, indent=2)
        
        logger.info(f"Test vectors saved to {output_file}")
        
        # Print summary
        for vector in vectors:
            logger.info(f"Generated {vector['id']}: "
                       f"SR={vector['sr']:.4f}, C={vector['c']:.4f}, "
                       f"Actions={len(vector['actions'])}")
        
        return 0
        
    except Exception as e:
        logger.error(f"Failed to save test vectors: {e}")
        return 1


if __name__ == "__main__":
    exit(main())
