"""Quantum-enhanced wrappers around SQUID formulas.

This module is intentionally decoupled from the main Flask app. It can be
used in research scripts or plugged into the decision pipeline later
without changing the existing API.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict

from ..squid_formulas import SuperRelationCalculator, CorrelationCalculator
from .quantum_superposition import QuantumSuperposition
from .conditional_collapse import ConditionalCollapse


@dataclass
class QuantumEnhancedCalculator:
    hardware_fingerprint: str | None = None

    def __post_init__(self) -> None:
        self.superposition = QuantumSuperposition(self.hardware_fingerprint)
        self.collapse = ConditionalCollapse(self.superposition)
        self._sr_calc = SuperRelationCalculator()
        self._c_calc = CorrelationCalculator()

    def calculate_quantum_sr(self, params: Dict[str, int], hardware_state: Dict[str, Any] | None = None) -> float:
        """SR with quantum-inspired behaviour.

        We compute the base SR, then build a superposition around it and
        optionally collapse to a concrete value depending on hardware_state.
        """

        base_sr = float(self._sr_calc.calculate(params))
        dist = self.superposition.create_superposition(base_sr)
        if self.collapse.should_collapse("merkle_validation", hardware_state or {}):
            return float(self.collapse.collapse_to_value(dist, "merkle_validation"))
        return float(dist.sample())

    def calculate_quantum_c(self, params: Dict[str, int], temporal_fingerprint: float | None = None) -> float:
        """C with temporal hardware dependency.

        temporal_fingerprint may be, for example, a normalized duration from
        TemporalFingerprint. For now we use it as a small perturbation around
        the base C.
        """

        base_c = float(self._c_calc.calculate(params))
        # Use temporal_fingerprint as an additive bias before superposition
        if temporal_fingerprint is not None:
            base_c = base_c + 0.05 * float(temporal_fingerprint)
        dist = self.superposition.create_superposition(base_c)
        if self.collapse.should_collapse("ai_decision_required", {"temporal": temporal_fingerprint}):
            return float(self.collapse.collapse_to_value(dist, "ai_decision_required"))
        return float(dist.sample())
