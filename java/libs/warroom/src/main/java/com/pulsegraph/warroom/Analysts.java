package com.pulsegraph.warroom;

import com.pulsegraph.compliance.TelemetrySanitizer;
import com.pulsegraph.compliance.TelemetrySanitizer.SanitizedText;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Deterministic specialists. Each one returns findings. None of them propose an action. */
final class Analysts {

  private static final Pattern ERROR =
      Pattern.compile("(?i)(error|exception|exhausted|timeout|fail)");

  private Analysts() {}

  static List<Finding> metrics(EvidenceBundle bundle) {
    List<Finding> out = new ArrayList<>();
    int i = 0;
    for (MetricSample sample : bundle.metrics()) {
      if (!(sample.value() >= sample.baseline() * 1.5)) {
        continue;
      }
      SanitizedText name = TelemetrySanitizer.sanitize(sample.name());
      out.add(
          new Finding(
              "metrics-" + i++,
              "metrics",
              sample.service(),
              sample.timestamp(),
              name.text(),
              Double.toString(sample.value()),
              Double.toString(sample.baseline()),
              name.instructionStripped(),
              name.secretRedacted()));
    }
    return out;
  }

  static List<Finding> logs(EvidenceBundle bundle) {
    List<Finding> out = new ArrayList<>();
    int i = 0;
    for (LogSample sample : bundle.logs()) {
      SanitizedText body = TelemetrySanitizer.sanitize(sample.message());
      boolean interesting = body.instructionStripped() || ERROR.matcher(body.text()).find();
      if (!interesting) {
        continue;
      }
      out.add(
          new Finding(
              "logs-" + i++,
              "logs",
              sample.service(),
              sample.timestamp(),
              "log",
              body.text(),
              "",
              body.instructionStripped(),
              body.secretRedacted()));
    }
    return out;
  }

  static List<Finding> topology(EvidenceBundle bundle) {
    List<String> neighbors = new ArrayList<>();
    for (GraphEdge edge : bundle.edges()) {
      if (bundle.focusService().equals(edge.from()) && !neighbors.contains(edge.to())) {
        neighbors.add(edge.to());
      }
      if (bundle.focusService().equals(edge.to()) && !neighbors.contains(edge.from())) {
        neighbors.add(edge.from());
      }
    }
    if (neighbors.isEmpty()) {
      return List.of();
    }
    return List.of(
        new Finding(
            "topology-0",
            "topology",
            bundle.focusService(),
            bundle.windowStart(),
            "dependencies",
            String.join(", ", neighbors),
            "",
            false,
            false));
  }

  static List<Finding> deploys(EvidenceBundle bundle) {
    List<Finding> out = new ArrayList<>();
    int i = 0;
    for (ChangeEvent change : bundle.changes()) {
      SanitizedText summary = TelemetrySanitizer.sanitize(change.summary());
      out.add(
          new Finding(
              "deploy-" + i++,
              "deploy",
              change.service(),
              change.timestamp(),
              change.kind() == null ? "change" : change.kind(),
              summary.text(),
              "",
              summary.instructionStripped(),
              summary.secretRedacted()));
    }
    return out;
  }
}
