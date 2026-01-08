#!/usr/bin/env python3
"""
SQUID Utilities - Helper functions for deterministic operations and validation
"""

import hashlib
import random
import numpy as np
import torch
import json
from typing import List, Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)


def set_deterministic_seed(seed_input: str, algorithm: str = "sha256") -> int:
    """
    Set deterministic seed from string input for reproducible operations
    
    Args:
        seed_input: String to derive seed from (e.g., seed_model_hash)
        algorithm: Hash algorithm to use for seed derivation
    
    Returns:
        Integer seed value used
    """
    # Create hash of input string
    hash_obj = hashlib.new(algorithm)
    hash_obj.update(seed_input.encode('utf-8'))
    hash_digest = hash_obj.digest()
    
    # Convert first 4 bytes to integer seed
    seed = int.from_bytes(hash_digest[:4], byteorder='big')
    
    # Ensure seed is in valid range for most RNGs
    seed = seed % (2**31 - 1)
    
    # Set seeds for all relevant libraries
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed(seed)
        torch.cuda.manual_seed_all(seed)
    
    # Set deterministic operations for PyTorch
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    
    logger.debug(f"Set deterministic seed {seed} from input '{seed_input[:16]}...'")
    return seed


def validate_features(features: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Validate and normalize feature data for model input
    
    Args:
        features: List of feature dictionaries
    
    Returns:
        Validated and normalized features
    
    Raises:
        ValueError: If features are invalid
    """
    if not features:
        raise ValueError("Features list cannot be empty")
    
    # Expected feature schema
    required_fields = {
        'depth': (int, float),
        'index': (int,),
        'index_hash': (int, float),
        'local_entropy': (int, float),
        'timestamp': (int, float),
        'global_L': (int,),
        'global_b': (int,),
        'global_m': (int,),
        'global_t': (int,),
        'last_access_count': (int, float),
        'leaf_hist_score': (int, float)
    }
    
    validated_features = []
    
    for i, feature in enumerate(features):
        if not isinstance(feature, dict):
            raise ValueError(f"Feature {i} must be a dictionary")
        
        validated_feature = {}
        
        # Check required fields
        for field, expected_types in required_fields.items():
            if field not in feature:
                raise ValueError(f"Feature {i} missing required field: {field}")
            
            value = feature[field]
            if not isinstance(value, expected_types):
                try:
                    # Try to convert to appropriate type
                    if int in expected_types:
                        value = int(value)
                    elif float in expected_types:
                        value = float(value)
                except (ValueError, TypeError):
                    raise ValueError(f"Feature {i} field '{field}' has invalid type: {type(value)}")
            
            validated_feature[field] = value
        
        # Validate value ranges
        _validate_feature_ranges(validated_feature, i)
        
        validated_features.append(validated_feature)
    
    logger.debug(f"Validated {len(validated_features)} features")
    return validated_features


def _validate_feature_ranges(feature: Dict[str, Any], index: int):
    """Validate feature value ranges"""
    validations = [
        ('depth', 1, 10, "Depth must be between 1 and 10"),
        ('index', 0, 100000, "Index must be non-negative and reasonable"),
        ('local_entropy', 0.0, 8.0, "Local entropy must be between 0 and 8"),
        ('global_b', 2, 16, "Branching factor must be between 2 and 16"),
        ('global_m', 1, 8, "Tree depth must be between 1 and 8"),
        ('global_t', 32, 512, "Leaf bits must be between 32 and 512"),
        ('last_access_count', 0, 1000000, "Access count must be non-negative"),
        ('leaf_hist_score', 0.0, 1.0, "Historical score must be between 0 and 1")
    ]
    
    for field, min_val, max_val, message in validations:
        if field in feature:
            value = feature[field]
            if not (min_val <= value <= max_val):
                raise ValueError(f"Feature {index}: {message}, got {value}")


def canonicalize_json(data: Any) -> str:
    """
    Create canonical JSON representation for cryptographic operations
    
    Args:
        data: Data to canonicalize
    
    Returns:
        Canonical JSON string
    """
    return json.dumps(data, sort_keys=True, separators=(',', ':'), ensure_ascii=True)


def bytes_to_hex(data: bytes) -> str:
    """Convert bytes to lowercase hex string"""
    return data.hex().lower()


def hex_to_bytes(hex_str: str) -> bytes:
    """Convert hex string to bytes"""
    return bytes.fromhex(hex_str)


def calculate_entropy(data: bytes) -> float:
    """
    Calculate Shannon entropy of byte data
    
    Args:
        data: Byte data to analyze
    
    Returns:
        Entropy value in bits
    """
    if not data:
        return 0.0
    
    # Count byte frequencies
    counts = np.bincount(data, minlength=256)
    probabilities = counts / len(data)
    
    # Calculate entropy
    entropy = 0.0
    for p in probabilities:
        if p > 0:
            entropy -= p * np.log2(p)
    
    return entropy


def secure_compare(a: str, b: str) -> bool:
    """
    Timing-safe string comparison
    
    Args:
        a: First string
        b: Second string
    
    Returns:
        True if strings are equal
    """
    if len(a) != len(b):
        return False
    
    result = 0
    for x, y in zip(a, b):
        result |= ord(x) ^ ord(y)
    
    return result == 0


def derive_deterministic_value(seed: str, context: str, length: int = 32) -> bytes:
    """
    Derive deterministic value from seed and context
    
    Args:
        seed: Base seed string
        context: Context string for derivation
        length: Output length in bytes
    
    Returns:
        Derived bytes
    """
    # Combine seed and context
    combined = f"{seed}|{context}"
    
    # Use SHAKE256 for variable-length output
    from hashlib import shake_256
    return shake_256(combined.encode('utf-8')).digest(length)


def format_duration(seconds: float) -> str:
    """Format duration in human-readable format"""
    if seconds < 1:
        return f"{seconds*1000:.1f}ms"
    elif seconds < 60:
        return f"{seconds:.1f}s"
    elif seconds < 3600:
        return f"{seconds/60:.1f}m"
    else:
        return f"{seconds/3600:.1f}h"


def log_performance(operation: str, duration: float, details: Optional[Dict] = None):
    """Log performance metrics"""
    details_str = ""
    if details:
        details_str = " " + " ".join(f"{k}={v}" for k, v in details.items())
    
    logger.info(f"PERF {operation}: {format_duration(duration)}{details_str}")


class PerformanceTimer:
    """Context manager for measuring operation performance"""
    
    def __init__(self, operation: str, details: Optional[Dict] = None):
        self.operation = operation
        self.details = details or {}
        self.start_time = None
        self.end_time = None
    
    def __enter__(self):
        import time
        self.start_time = time.perf_counter()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        import time
        self.end_time = time.perf_counter()
        duration = self.end_time - self.start_time
        log_performance(self.operation, duration, self.details)


def validate_branching_consistency(params: Dict[str, int], leaves_count: int) -> bool:
    """
    Validate that leaf count matches branching parameters
    
    Args:
        params: Branching parameters (b, m, t)
        leaves_count: Actual number of leaves
    
    Returns:
        True if consistent
    """
    expected_leaves = params['b'] ** params['m']
    return leaves_count == expected_leaves


def create_audit_hash(data: Dict[str, Any]) -> str:
    """
    Create audit hash for data integrity verification
    
    Args:
        data: Data to hash
    
    Returns:
        SHA-256 hex hash
    """
    canonical = canonicalize_json(data)
    hash_obj = hashlib.sha256(canonical.encode('utf-8'))
    return hash_obj.hexdigest()


def verify_signature_format(signature: str) -> bool:
    """
    Verify post-quantum signature format
    
    Args:
        signature: Signature string to verify
    
    Returns:
        True if format is valid
    """
    # Check for expected prefix
    if not signature.startswith(('DILITHIUM3_', 'SPHINCS_', 'FALCON_')):
        return False
    
    # Check if remainder is valid base64
    try:
        import base64
        sig_data = signature.split('_', 1)[1]
        base64.b64decode(sig_data)
        return True
    except Exception:
        return False


def rate_limit_check(operation: str, max_per_minute: int = 60) -> bool:
    """
    Simple rate limiting check (in-memory, for demonstration)
    In production, use Redis or similar
    
    Args:
        operation: Operation identifier
        max_per_minute: Maximum operations per minute
    
    Returns:
        True if operation is allowed
    """
    import time
    
    # Simple in-memory rate limiting
    if not hasattr(rate_limit_check, '_counters'):
        rate_limit_check._counters = {}
    
    now = time.time()
    minute_key = int(now // 60)
    operation_key = f"{operation}:{minute_key}"
    
    # Clean old entries
    keys_to_remove = [k for k in rate_limit_check._counters.keys() 
                     if int(k.split(':')[1]) < minute_key - 1]
    for key in keys_to_remove:
        del rate_limit_check._counters[key]
    
    # Check current count
    current_count = rate_limit_check._counters.get(operation_key, 0)
    if current_count >= max_per_minute:
        logger.warning(f"Rate limit exceeded for {operation}: {current_count}/{max_per_minute}")
        return False
    
    # Increment counter
    rate_limit_check._counters[operation_key] = current_count + 1
    return True


# Test functions
def test_utilities():
    """Test utility functions"""
    print("Testing SQUID Utilities...")
    
    # Test deterministic seed
    seed1 = set_deterministic_seed("test_input")
    seed2 = set_deterministic_seed("test_input")
    assert seed1 == seed2, "Seeds should be deterministic"
    print(f"✓ Deterministic seed: {seed1}")
    
    # Test feature validation
    valid_features = [{
        'depth': 3, 'index': 0, 'index_hash': 0, 'local_entropy': 7.5,
        'timestamp': 1640995200000, 'global_L': 64, 'global_b': 4,
        'global_m': 3, 'global_t': 128, 'last_access_count': 0,
        'leaf_hist_score': 0.5
    }]
    
    validated = validate_features(valid_features)
    assert len(validated) == 1, "Should validate 1 feature"
    print("✓ Feature validation")
    
    # Test entropy calculation
    test_data = b"Hello, World!"
    entropy = calculate_entropy(test_data)
    assert 0 <= entropy <= 8, f"Entropy should be 0-8, got {entropy}"
    print(f"✓ Entropy calculation: {entropy:.2f}")
    
    # Test performance timer
    with PerformanceTimer("test_operation", {"items": 100}):
        import time
        time.sleep(0.1)
    print("✓ Performance timer")
    
    print("All utility tests passed!")


if __name__ == "__main__":
    test_utilities()
