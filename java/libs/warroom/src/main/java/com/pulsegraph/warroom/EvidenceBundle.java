package com.pulsegraph.warroom;

import java.util.ArrayList;
import java.util.List;

/** Telemetry the specialists are allowed to read. It is data, not a prompt. */
public record EvidenceBundle(
    String focusService,
    List<String> serviceSlice,
    String windowStart,
    String windowEnd,
    List<MetricSample> metrics,
    List<LogSample> logs,
    List<GraphEdge> edges,
    List<ChangeEvent> changes) {

  public EvidenceBundle {
    focusService = focusService == null ? "unknown" : focusService;
    serviceSlice = serviceSlice == null ? List.of(focusService) : List.copyOf(serviceSlice);
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
    logs = logs == null ? List.of() : List.copyOf(logs);
    edges = edges == null ? List.of() : List.copyOf(edges);
    changes = changes == null ? List.of() : List.copyOf(changes);
  }

  public static EvidenceBundle empty(String service) {
    return new EvidenceBundle(service, List.of(service), null, null, List.of(), List.of(), List.of(), List.of());
  }

  /** Second-pass question: keep the focus service and drop everyone else's signals. */
  public EvidenceBundle focusOnly() {
    return new EvidenceBundle(
        focusService,
        serviceSlice,
        windowStart,
        windowEnd,
        metrics.stream().filter(m -> focusService.equals(m.service())).toList(),
        logs.stream().filter(l -> focusService.equals(l.service())).toList(),
        edges,
        changes.stream().filter(c -> focusService.equals(c.service())).toList());
  }

  public List<String> boundary() {
    if (serviceSlice.contains(focusService)) {
      return serviceSlice;
    }
    List<String> withFocus = new ArrayList<>(serviceSlice);
    withFocus.add(focusService);
    return List.copyOf(withFocus);
  }
}
