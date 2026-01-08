"""Research utilities for collecting hardware fingerprint datasets.

This module is intended for offline experiments and does not participate
in the main production pipeline.
"""

from __future__ import annotations

import time
import platform
import hashlib
from dataclasses import dataclass, field
from typing import Any, Dict


@dataclass
class FingerprintDataset:
    fingerprints: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    measurements: Dict[str, Dict[str, Any]] = field(default_factory=dict)

    def _base_system_info(self) -> Dict[str, Any]:
        return {
            "system": platform.system(),
            "release": platform.release(),
            "version": platform.version(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "python_version": platform.python_version(),
        }

    def measure_temporal_characteristics(self) -> Dict[str, Any]:
        samples = []
        for _ in range(32):
            start = time.perf_counter_ns()
            acc = 0
            for i in range(50_000):
                acc += i
            end = time.perf_counter_ns()
            samples.append(max(1, end - start))
        mean = sum(samples) / len(samples)
        var = sum((x - mean) ** 2 for x in samples) / len(samples)
        return {"samples": samples, "mean": mean, "std": var ** 0.5}

    def measure_microarchitectural_features(self) -> Dict[str, Any]:
        return {
            "logical_cores": getattr(__import__("os"), "cpu_count")() or 1,
            "max_memory_hint_mb": None,
        }

    def measure_quantum_variations(self) -> Dict[str, Any]:
        # Placeholder for future integration with quantum_inspired module
        return {"note": "quantum variations are modeled in quantum_inspired"}

    def measure_assembly_behavior(self) -> Dict[str, Any]:
        # At this level we only record a stable hash of system description
        info = str(self._base_system_info()).encode("utf-8")
        digest = hashlib.sha256(info).hexdigest()
        return {"system_hash": digest}

    def collect_fingerprint(self, hardware_id: str) -> Dict[str, Any]:
        """Collect a comprehensive (synthetic) hardware fingerprint."""

        fp = {
            "temporal": self.measure_temporal_characteristics(),
            "microarchitectural": self.measure_microarchitectural_features(),
            "quantum_states": self.measure_quantum_variations(),
            "assembly_hash": self.measure_assembly_behavior(),
        }
        self.fingerprints[hardware_id] = fp
        return fp

    def validate_reproducibility(self, hardware_id: str, trials: int = 10) -> Dict[str, Any]:
        """Validate same-hardware reproducibility using multiple trials."""

        results = []
        for _ in range(trials):
            results.append(self.measure_temporal_characteristics()["mean"])
        mean = sum(results) / len(results)
        var = sum((x - mean) ** 2 for x in results) / len(results)
        return {
            "hardware_id": hardware_id,
            "means": results,
            "mean_of_means": mean,
            "std_of_means": var ** 0.5,
        }
