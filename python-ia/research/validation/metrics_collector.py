"""Metrics collection helpers for SQUID research.

Provides basic aggregation for performance and security experiments.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List


@dataclass
class MetricsCollector:
    metrics: Dict[str, List[float]] = field(default_factory=lambda: {
        "build_time": [],
        "verification_time": [],
        "false_positive_rate": [],
        "attack_resistance": [],
    })

    def collect_performance_metrics(self, test_cases: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Measure performance impact of new features.

        Each test_case may contain "build_time" and "verification_time" in
        seconds. We aggregate simple statistics.
        """

        for case in test_cases:
            bt = case.get("build_time")
            vt = case.get("verification_time")
            if bt is not None:
                self.metrics["build_time"].append(float(bt))
            if vt is not None:
                self.metrics["verification_time"].append(float(vt))

        return {
            "build_time": self._summary(self.metrics["build_time"]),
            "verification_time": self._summary(self.metrics["verification_time"]),
        }

    def collect_security_metrics(self, attack_scenarios: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Measure security improvements based on attack outcomes.

        Each scenario may provide "success" boolean.
        """

        successes = 0
        total = 0
        for scenario in attack_scenarios:
            if "success" in scenario:
                total += 1
                if scenario["success"]:
                    successes += 1
        rate = (successes / total) if total else 0.0
        self.metrics["attack_resistance"].append(1.0 - rate)
        return {
            "attack_success_rate": rate,
            "attack_resistance": 1.0 - rate,
        }

    def _summary(self, values: List[float]) -> Dict[str, float]:
        if not values:
            return {"count": 0, "mean": 0.0, "min": 0.0, "max": 0.0}
        count = len(values)
        mean = sum(values) / count
        return {
            "count": count,
            "mean": mean,
            "min": min(values),
            "max": max(values),
        }
