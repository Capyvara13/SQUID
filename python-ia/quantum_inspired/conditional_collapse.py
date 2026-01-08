"""Conditional collapse logic for quantum-inspired behaviour.

This module implements the ConditionalCollapse abstraction in a way that
is easy to integrate with existing SR/C calculations but remains fully
optional for the production pipeline.
"""

from __future__ import annotations

import hashlib
from typing import Any, Dict, Iterable

from .quantum_superposition import QuantumSuperposition, StateDistribution


class ConditionalCollapse:
    def __init__(self, superposition: QuantumSuperposition) -> None:
        self.superposition = superposition
        self.collapse_triggers = [
            "merkle_validation",
            "ai_decision_required",
            "external_event",
            "time_based_decay",
        ]

    def _score_context(self, trigger: str, context: Dict[str, Any] | None) -> float:
        h = hashlib.sha256()
        h.update(trigger.encode("utf-8"))
        if context:
            for k in sorted(context.keys()):
                h.update(str(k).encode("utf-8"))
                h.update(str(context[k]).encode("utf-8"))
        val = int.from_bytes(h.digest()[:8], "big", signed=False)
        return (val % 10_000) / 10_000.0  # [0,1)

    def should_collapse(self, trigger: str, context: Dict[str, Any] | None) -> bool:
        """Decide if a state should collapse given trigger and context.

        The decision is probabilistic but deterministic for a given
        (hardware_fingerprint, trigger, context).
        """

        score = self._score_context(trigger, context)
        # For now we use a simple threshold based on trigger category
        if trigger == "merkle_validation":
            threshold = 0.4
        elif trigger == "ai_decision_required":
            threshold = 0.2
        elif trigger == "time_based_decay":
            threshold = 0.6
        else:
            threshold = 0.5
        return score >= threshold

    def collapse_to_value(self, distribution: StateDistribution, trigger: str) -> float:
        return self.superposition.collapse_to_value(distribution, trigger)
