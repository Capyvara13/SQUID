#!/usr/bin/env python3
"""
Compare test vectors between Java and Python implementations
Verify bytewise compatibility
"""

import json
import sys
import logging
from typing import Dict, Any

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def load_test_vectors(file_path: str) -> Dict[str, Any]:
    """Load test vectors from JSON file"""
    try:
        with open(file_path, 'r') as f:
            data = json.load(f)
        
        # Handle different JSON structures
        if 'test_vectors' in data:
            return data['test_vectors']
        elif isinstance(data, list):
            return data
        else:
            return data
    except Exception as e:
        logger.error(f"Failed to load {file_path}: {e}")
        return []


def compare_vectors(java_vectors, python_vectors) -> bool:
    """Compare Java and Python test vectors"""
    success = True
    
    # Ensure both have same number of vectors
    if len(java_vectors) != len(python_vectors):
        logger.error(f"Vector count mismatch: Java={len(java_vectors)}, Python={len(python_vectors)}")
        return False
    
    # Compare each vector
    for i, (java_vec, python_vec) in enumerate(zip(java_vectors, python_vectors)):
        logger.info(f"Comparing vector {i+1}...")
        
        # Compare IDs
        java_id = java_vec.get('id', f'tv{i+1}')
        python_id = python_vec.get('id', f'tv{i+1}')
        
        if java_id != python_id:
            logger.error(f"ID mismatch: Java={java_id}, Python={python_id}")
            success = False
            continue
        
        # Compare parameters
        java_params = java_vec.get('params', {})
        python_params = python_vec.get('params', {})
        
        if java_params != python_params:
            logger.error(f"Params mismatch for {java_id}: Java={java_params}, Python={python_params}")
            success = False
        
        # Compare SR and C with tolerance
        tolerance = 1e-6
        
        java_sr = java_vec.get('sr', 0)
        python_sr = python_vec.get('sr', 0)
        if abs(java_sr - python_sr) > tolerance:
            logger.error(f"SR mismatch for {java_id}: Java={java_sr}, Python={python_sr}")
            success = False
        
        java_c = java_vec.get('c', 0)
        python_c = python_vec.get('c', 0)
        if abs(java_c - python_c) > tolerance:
            logger.error(f"C mismatch for {java_id}: Java={java_c}, Python={python_c}")
            success = False
        
        # Compare root key if available
        java_root = java_vec.get('root_key_hex', '')
        python_root = python_vec.get('root_key_hex', '')
        if java_root and python_root and java_root != python_root:
            logger.error(f"Root key mismatch for {java_id}")
            success = False
        
        # Compare merkle root
        java_merkle = java_vec.get('merkle_root_hex', '')
        python_merkle = python_vec.get('merkle_root_hex', '')
        if java_merkle and python_merkle and java_merkle != python_merkle:
            logger.error(f"Merkle root mismatch for {java_id}")
            success = False
        
        # Compare sample leaves
        java_leaves = java_vec.get('leaves', [])
        python_leaves = python_vec.get('leaves', [])
        min_leaves = min(len(java_leaves), len(python_leaves))
        
        for j in range(min(min_leaves, 5)):  # Compare first 5 leaves
            if java_leaves[j] != python_leaves[j]:
                logger.error(f"Leaf {j} mismatch for {java_id}")
                success = False
        
        if success:
            logger.info(f"✓ Vector {java_id} matches between implementations")
        else:
            logger.error(f"✗ Vector {java_id} has mismatches")
    
    return success


def main():
    """Main comparison function"""
    if len(sys.argv) != 3:
        print("Usage: python compare-vectors.py <java_file> <python_file>")
        return 1
    
    java_file = sys.argv[1]
    python_file = sys.argv[2]
    
    logger.info(f"Comparing {java_file} vs {python_file}")
    
    # Load vectors
    java_vectors = load_test_vectors(java_file)
    python_vectors = load_test_vectors(python_file)
    
    if not java_vectors or not python_vectors:
        logger.error("Failed to load test vectors")
        return 1
    
    # Compare
    if compare_vectors(java_vectors, python_vectors):
        logger.info("✓ All test vectors match between Java and Python implementations!")
        return 0
    else:
        logger.error("✗ Test vector mismatches found!")
        return 1


if __name__ == "__main__":
    exit(main())
