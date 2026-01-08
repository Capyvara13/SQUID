"""Decision engine to convert normalized SR and C into rotation plans.

This module provides a lightweight deterministic planner that recommends which
leaf indices should be rotated given normalized SR and C in [0,1]. It is
intended to be fast, stable and easy to test; more complex ML policies can
replace it later.
"""
from typing import List, Dict, Any
import math
import random


def plan_rotations(num_leaves: int, sr_norm: float, c_norm: float, seed: int = None) -> Dict[str, Any]:
    """Return a rotation plan.

    - Choose rotation percentage between 3% and 20% based on risk score derived
      from SR and C (lower SR or higher C => higher rotation).
    - Returns list of indices to rotate.
    """
    if seed is not None:
        rnd = random.Random(seed)
    else:
        rnd = random.Random(0)

    # risk: if SR low (close to 0) or C high (close to 1) => increase rotation
    risk = (1.0 - sr_norm) * 0.6 + c_norm * 0.4

    # map risk [0,1] to percent [0.03, 0.20]
    pct = 0.03 + (0.20 - 0.03) * min(1.0, max(0.0, risk))

    count = max(1, int(math.ceil(num_leaves * pct)))

    indices = list(range(num_leaves))
    rnd.shuffle(indices)
    selected = sorted(indices[:count])

    return {
        "rotation_percentage": pct,
        "rotation_count": count,
        "indices": selected,
        "risk": risk
    }
