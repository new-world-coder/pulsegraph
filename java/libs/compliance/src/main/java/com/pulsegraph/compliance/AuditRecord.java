package com.pulsegraph.compliance;

import java.time.Instant;
import java.util.Map;

/**
 * One entry in the immutable decision audit trail. Records are hash-chained: each record's
 * {@code hash} covers its own fields plus the {@code prevHash} of the record before it, making
 * silent tampering or deletion detectable. Maps to EU AI Act Art.12 traceability and DORA evidence.
 */
public record AuditRecord(
    long sequence,
    Instant timestamp,
    String agentId,
    String actorType,
    String actor,
    String eventType,
    Map<String, Object> attributes,
    String prevHash,
    String hash) {

  public AuditRecord {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }
}
