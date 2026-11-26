package com.pulsegraph.warroom;

import com.pulsegraph.compliance.RemediationPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ranks findings. The remediation agent receives the hypothesis only. */
final class Synthesis {

  static final double THRESHOLD = 0.55;

  private Synthesis() {}

  static Hypothesis rank(List<Finding> findings, String focus, int round) {
    double confidence = 0.15;
    List<String> supports = new ArrayList<>();
    List<String> contradicts = new ArrayList<>();
    Finding deploy = null;
    boolean metricShift = false;
    for (Finding finding : findings) {
      boolean onFocus = focus.equals(finding.service());
      if (finding.instructionStripped()) {
        contradicts.add(finding.id() + " contained instructions and was kept as data");
        continue;
      }
      switch (finding.agent()) {
        case "deploy" -> {
          if (onFocus) {
            deploy = finding;
            supports.add(finding.id());
            confidence += 0.35;
          }
        }
        case "metrics" -> {
          if (onFocus) {
            metricShift = true;
            supports.add(finding.id());
            confidence += 0.25;
          } else {
            contradicts.add(finding.id() + " is outside " + focus);
            confidence -= 0.05;
          }
        }
        case "logs" -> {
          if (onFocus) {
            supports.add(finding.id());
            confidence += 0.15;
          } else {
            contradicts.add(finding.id() + " is outside " + focus);
            confidence -= 0.05;
          }
        }
        case "topology" -> {
          if (onFocus) {
            supports.add(finding.id());
            confidence += 0.05;
          }
        }
        default -> {
        }
      }
    }
    Cause cause = Cause.UNKNOWN;
    if (deploy != null) {
      String blob = (deploy.name() + " " + deploy.value()).toLowerCase();
      cause =
          blob.contains("pool") || blob.contains("config") || blob.contains("flag")
              ? Cause.CONFIG
              : Cause.DEPLOY;
    } else if (metricShift) {
      cause = Cause.SATURATION;
    }
    confidence = Math.max(0, Math.min(0.95, confidence));
    String title;
    if (deploy != null && !deploy.value().isBlank()) {
      title = deploy.value();
    } else if (metricShift) {
      title = "Latency or saturation shift on " + focus;
    } else {
      title = "Evidence is not sufficient for " + focus;
    }
    if (contradicts.isEmpty()) {
      contradicts.add("No contradicting finding in this round");
    }
    return new Hypothesis(
        "h-" + round,
        title,
        confidence,
        cause,
        List.copyOf(supports),
        List.copyOf(contradicts),
        round);
  }

  static RemediationPlan propose(Hypothesis hypothesis, String service) {
    List<String> evidence = hypothesis.supports();
    return switch (hypothesis.cause()) {
      case CONFIG ->
          new RemediationPlan(
              "set-connection-pool",
              service,
              Map.of("poolSize", "50"),
              "Latency on " + service + " moves back toward its baseline.",
              "Restore the previous pool size.",
              evidence);
      case DEPLOY ->
          new RemediationPlan(
              "rollback-release",
              service,
              Map.of(),
              "Latency and errors on " + service + " return to the pre-deploy baseline.",
              "Roll forward to the release that was reverted.",
              evidence);
      case SATURATION ->
          new RemediationPlan(
              "scale-replicas",
              service,
              Map.of("replicas", "3"),
              "Saturation on " + service + " falls as capacity is added.",
              "Scale back to the previous replica count.",
              evidence);
      case UNKNOWN -> null;
    };
  }
}
