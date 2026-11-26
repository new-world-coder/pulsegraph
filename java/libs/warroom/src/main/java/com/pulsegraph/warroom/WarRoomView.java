package com.pulsegraph.warroom;

import com.pulsegraph.compliance.RemediationPlan;
import java.util.List;

public record WarRoomView(
    String id,
    String service,
    String environment,
    String phase,
    int rounds,
    String followUp,
    List<Finding> findings,
    Hypothesis hypothesis,
    RemediationPlan proposal,
    String planHash,
    String gateEffect,
    String gateReason,
    String approvalId,
    String model,
    String modelVersion,
    List<String> timeline,
    CanaryResult canary) {

  public WarRoomView {
    findings = findings == null ? List.of() : List.copyOf(findings);
    timeline = timeline == null ? List.of() : List.copyOf(timeline);
  }

  WarRoomView withPhase(String nextPhase, List<String> nextTimeline) {
    return new WarRoomView(
        id,
        service,
        environment,
        nextPhase,
        rounds,
        followUp,
        findings,
        hypothesis,
        proposal,
        planHash,
        gateEffect,
        gateReason,
        approvalId,
        model,
        modelVersion,
        nextTimeline,
        canary);
  }

  WarRoomView withCanary(String nextPhase, List<String> nextTimeline, CanaryResult next) {
    return new WarRoomView(
        id,
        service,
        environment,
        nextPhase,
        rounds,
        followUp,
        findings,
        hypothesis,
        proposal,
        planHash,
        gateEffect,
        gateReason,
        approvalId,
        model,
        modelVersion,
        nextTimeline,
        next);
  }
}
