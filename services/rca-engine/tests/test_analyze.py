"""Smoke tests for analyze_incident stub."""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from main import analyze_incident


def test_analyze_incident_returns_stub_result() -> None:
    incident = {"incident_id": "inc-001", "severity": "critical"}
    result = analyze_incident(incident)
    assert result["incident_id"] == "inc-001"
    assert "root_cause" in result
    assert isinstance(result["contributing_factors"], list)


def test_analyze_incident_handles_missing_id() -> None:
    result = analyze_incident({})
    assert result["incident_id"] == "unknown"
