"""Quantum-inspired superposition utilities.

This module provides a lightweight, deterministic implementation of the
QuantumSuperposition abstraction described in the research plan. It does
not depend on any external quantum SDKs and is safe to import anywhere
in the python-ia service.
"""

from __future__ import annotations

import hashlib
import math
import os
from dataclasses import dataclass
from typing import Any, Dict

import numpy as np


@dataclass
class StateDistribution:
    base_value: float
    fingerprint: str
    mean: float
    std: float

    def sample(self, seed: str | None = None) -> float:
        """Sample a value deterministically when a seed is provided.

        If seed is None, sampling is pseudo-random but still derived from the
        fingerprint to keep behaviour hardware-tied.
        """

        # Build a deterministic seed from fingerprint + optional seed label
        h = hashlib.sha256()
        h.update(self.fingerprint.encode("utf-8"))
        if seed:
            h.update(seed.encode("utf-8"))
        full_seed = int.from_bytes(h.digest()[:8], "big", signed=False)
        rng = np.random.default_rng(full_seed)
        return float(rng.normal(self.mean, self.std))


class QuantumSuperposition:
    def __init__(self, hardware_fingerprint: str | None = None) -> None:
        if hardware_fingerprint is None:
            hardware_fingerprint = self._derive_hardware_fingerprint()
        self.fingerprint = hardware_fingerprint

    def _derive_hardware_fingerprint(self) -> str:
        """Create a simple hardware/environment fingerprint.

        Uses process + OS information; this is not security-critical but is
        enough to introduce environment dependence.
        """

        info = "|".join(
            [
                os.name,
                os.getenv("COMPUTERNAME", ""),
                os.getenv("HOSTNAME", ""),
                os.uname().sysname if hasattr(os, "uname") else "",
                os.uname().machine if hasattr(os, "uname") else "",
            ]
        )
        return hashlib.sha256(info.encode("utf-8")).hexdigest()

    def create_superposition(self, base_value: float) -> StateDistribution:
        """Create a distribution of possible states around base_value.

        The variance is tied to the hardware fingerprint so that different
        hosts induce slightly different spreads, while a given host behaves
        deterministically.
        """

        # Map a portion of the fingerprint into [0.5, 2.0] to scale variance
        scale_raw = int(self.fingerprint[:4], 16)
        scale = 0.5 + (scale_raw / 0xFFFF) * 1.5
        mean = float(base_value)
        std = max(1e-9, abs(base_value) * 0.05 * scale)
        return StateDistribution(base_value=mean, fingerprint=self.fingerprint, mean=mean, std=std)

    def collapse_to_value(self, distribution: StateDistribution, trigger_condition: str) -> float:
        """Collapse distribution to a specific value when triggered.

        The trigger_condition label is used to derive a deterministic seed,
        ensuring that for a given hardware fingerprint and trigger the
        collapse result is reproducible.
        """

        return distribution.sample(seed=trigger_condition)
