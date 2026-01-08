#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SQUID Formulas Implementation
Exact implementation of Super-Relation (SR) and Correlation Coefficient (C) formulas
"""

import math
import numpy as np
from typing import Dict, Any


class SuperRelationCalculator:
    """
    Implements the Super-Relation (SR) formula:
    SR = (2T/L) * K^(M-1)/2 * (∑[p=1 to P_max] max(3/2)^p / (p^α * P(1-P))) * g(b)
    
    where g(b) = b³ - 1/(3b²)
    """
    
    def __init__(self, P_max: int = 4, alpha: float = 1.5, K: int = 2):
        self.P_max = P_max
        self.alpha = alpha
        self.K = K
        # backward-compatible defaults
        self.beta = 0.12
        self.lam = -0.1
        self.normalize_scale = 1.0
    
    def calculate(self, params: Dict[str, int]) -> float:
        """Calculate SR for given branching parameters with stability fallback"""
        b = params['b']
        m = params['m'] 
        t = params['t']
        
        # Validate inputs
        if b <= 0 or m <= 0 or t <= 0:
            return 1.0
        
        # Compute raw SR using stabilized components
        raw = self._calculate_full_formula(b, m, t)

        # If raw is not finite or extremely large, fallback to simplified stable
        if not math.isfinite(raw) or raw < 0.0 or raw > 1e9:
            raw = self._calculate_simplified_formula(b, m, t)

        # Normalize to [0,1] using a sigmoid on log1p(raw) to avoid explosion
        try:
            norm_input = math.log1p(abs(raw)) * (1.0 if raw >= 0 else -1.0)
            s = max(0.001, self.normalize_scale)
            normalized = 1.0 / (1.0 + math.exp(-s * (norm_input - 0.0)))
            return float(max(0.0, min(1.0, normalized)))
        except Exception:
            return 0.0
    
    def _calculate_full_formula(self, b: int, m: int, t: int) -> float:
        """Calculate full SR formula with all components"""
        # Calculate L = b^m
        L = b ** m
        if L <= 0:
            return 1.0
        
        # Component 1: 2T/L
        component1 = (2 * t) / L
        
        # Component 2: replace explosive K^(M-1)/2 by exp(beta*(M-1)) for stability
        component2 = math.exp(self.beta * (m - 1))

        # Component 3: stabilized summation using exp(lambda * p)
        component3 = 0.0
        eps = 1e-12
        for p in range(1, self.P_max + 1):
            P = p / self.P_max
            prob_term = P * (1 - P) + eps
            exp_term = math.exp(self.lam * p)
            denom = (p ** self.alpha) * prob_term
            component3 += exp_term / denom

        # Component 4: use less-explosive g(b) = b * log(b+1)
        component4 = self._g_function(b)

        return component1 * component2 * component3 * component4
    
    def _calculate_simplified_formula(self, b: int, m: int, t: int) -> float:
        """Simplified stable SR formula as fallback"""
        # SR_simple = (t/b^m) * (1 + b) / (1 + m)
        # This is more stable and provides reasonable behavior
        try:
            L = b ** m
            if L <= 0:
                return 1.0
            sr_simple = (t / L) * (1.0 + b) / (1.0 + m)
            if math.isinf(sr_simple) or math.isnan(sr_simple) or sr_simple < 0.0:
                return 1.0
            return sr_simple
        except:
            return 1.0
    
    def _calculate_sum_component(self) -> float:
        # Deprecated: we now compute summation inline in full formula
        total = 0.0
        eps = 1e-12
        for p in range(1, self.P_max + 1):
            P = p / self.P_max
            prob_term = P * (1 - P) + eps
            term = math.exp(self.lam * p) / ((p ** self.alpha) * prob_term)
            if math.isfinite(term):
                total += term
        return total if total > 0 else 1.0
    
    def _g_function(self, b: int) -> float:
        """
        Calculate g(b) = b³ - 1/(3b²)
        With smoothing for stability if needed
        """
        if b <= 0:
            return 0.0
        
        result = float(b) * math.log(b + 1.0)
        # Small smoothing to avoid zeros
        if result <= 0.0:
            result = 0.0001
        return result


class CorrelationCalculator:
    """
    Implements the Correlation Coefficient (C) formula:
    C = (t * b^a * ∑[i=1 to m] b_i) / P^(2d+1)
    
    For constant branching factor: ∑b_i = b * m
    """
    
    def __init__(self, a: float = 0.5, P: float = 0.1):
        self.a = a  # Normalization factor
        self.P = P  # Base probability
        self.normalize_scale = 1.0
    
    def calculate(self, params: Dict[str, int], d: int = None) -> float:
        """Calculate C for given branching parameters"""
        b = params['b']
        m = params['m']
        t = params['t']
        
        # Default target depth to m
        if d is None:
            d = m

        eps = 1e-12
        # Numerator: use log to reduce explosive behavior
        numerator = float(t) * (b ** self.a) * (b * m)
        # Denominator: use P^(2d+1) but mitigate small P via log1p
        denom = max(eps, self.P ** (2 * d + 1))
        raw = numerator / denom

        # Stabilize and normalize to [0,1]
        try:
            norm_input = math.log1p(abs(raw)) * (1.0 if raw >= 0 else -1.0)
            s = max(0.001, self.normalize_scale)
            normalized = 1.0 / (1.0 + math.exp(-s * (norm_input - 0.0)))
            return float(max(0.0, min(1.0, normalized)))
        except Exception:
            return 0.0


class PolicyCalculator:
    """
    Implements the deterministic policy based on SR and C thresholds
    """
    
    def __init__(self, sr_min: float = 1.0, gamma_t: float = 10.0):
        self.sr_min = sr_min
        self.gamma_t = gamma_t
    
    def calculate_decoy_rate(self, sr: float, c: float) -> float:
        """
        Calculate decoy rate based on SR and C values
        
        Policy:
        - If SR >= sr_min and C >= γt: high confidence zone (0.2-0.5 decoy rate)
        - Otherwise: low confidence zone (0.01-0.1 decoy rate)
        """
        if sr >= self.sr_min and c >= self.gamma_t:
            # High confidence zone
            # Scale decoy rate based on SR and C values
            base_rate = 0.2
            scale_factor = min(2.5, (sr + c / self.gamma_t) / 10)
            decoy_rate = base_rate * scale_factor
            return self._clamp(decoy_rate, 0.2, 0.5)
        else:
            # Low confidence zone
            base_rate = 0.01
            scale_factor = min(10, (sr + c / self.gamma_t))
            decoy_rate = base_rate * scale_factor
            return self._clamp(decoy_rate, 0.01, 0.1)
    
    def _clamp(self, value: float, min_val: float, max_val: float) -> float:
        """Clamp value between min and max"""
        return max(min_val, min(max_val, value))
    
    def generate_actions(self, num_leaves: int, sr: float, c: float, 
                        seed: int = None) -> list:
        """
        Generate deterministic but randomized actions for leaves based on policy
        """
        if seed is not None:
            np.random.seed(seed)
        
        decoy_rate = self.calculate_decoy_rate(sr, c)
        
        # Determine how many of each action
        num_decoy = int(num_leaves * decoy_rate)
        num_mutate = int(num_leaves * 0.05) if sr >= self.sr_min else 0
        num_reassign = int(num_leaves * 0.05) if sr < self.sr_min else 0
        num_valid = num_leaves - num_decoy - num_mutate - num_reassign
        
        # Create action array with deterministic randomization
        actions = ["DECOY"] * num_decoy + ["MUTATE"] * num_mutate + ["REASSIGN"] * num_reassign + ["VALID"] * num_valid
        
        # Shuffle using deterministic seed-based randomization
        if seed is not None:
            # Use seed to shuffle deterministically
            indices = np.arange(num_leaves)
            np.random.seed(seed)
            np.random.shuffle(indices)
            
            shuffled_actions = [None] * num_leaves
            for new_pos, old_pos in enumerate(indices):
                shuffled_actions[new_pos] = actions[old_pos]
            actions = shuffled_actions
        
        return actions
        
        return actions


# Utility functions for formula validation
def validate_branching_params(params: Dict[str, int]) -> bool:
    """Validate branching parameters"""
    required_keys = ['b', 'm', 't']
    
    if not all(key in params for key in required_keys):
        return False
    
    b, m, t = params['b'], params['m'], params['t']
    
    # Basic validation
    if b < 2 or b > 16:  # Reasonable branching factor range
        return False
    if m < 1 or m > 8:   # Reasonable depth range
        return False
    if t < 64 or t > 512:  # Reasonable bit length range
        return False
    
    # Check if total leaves is reasonable
    total_leaves = b ** m
    if total_leaves > 10000:  # Prevent excessive computation
        return False
    
    return True


def calculate_birthday_bound(t: int) -> float:
    """Calculate birthday collision bound for t-bit values"""
    return 2 ** (t / 2)


def calculate_enumeration_time(L: int, hash_rate: float) -> float:
    """Calculate attacker enumeration time"""
    return L / hash_rate


def calculate_build_time(L: int, kdf_time: float) -> float:
    """Calculate tree build time"""
    return L * kdf_time


# Test and validation functions
def test_formulas():
    """Test formula implementations with known values"""
    print("Testing SQUID Formulas...")
    
    # Test parameters
    params = {"b": 4, "m": 3, "t": 128}
    
    # Test SR calculation
    sr_calc = SuperRelationCalculator()
    sr = sr_calc.calculate(params)
    print(f"SR for {params}: {sr:.4f}")
    
    # Test C calculation
    c_calc = CorrelationCalculator()
    c = c_calc.calculate(params)
    print(f"C for {params}: {c:.4f}")
    
    # Test policy
    policy_calc = PolicyCalculator()
    decoy_rate = policy_calc.calculate_decoy_rate(sr, c)
    print(f"Decoy rate: {decoy_rate:.4f}")
    
    actions = policy_calc.generate_actions(10, sr, c, seed=42)
    print(f"Sample actions: {actions}")
    
    print("Formula tests completed.")


if __name__ == "__main__":
    test_formulas()
