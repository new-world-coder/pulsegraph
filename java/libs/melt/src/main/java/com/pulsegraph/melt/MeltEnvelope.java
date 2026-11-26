package com.pulsegraph.melt;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record MeltEnvelope(
    String id,
    String tenantId,
    String sourceVendor,
    String signalType,
    long timestampUnixNs,
    long ingestedAtUnixNs,
    String serviceName,
    String environment,
    String traceId,
    String spanId,
    Map<String, String> labels,
    JsonNode payload,
    boolean normalized) {}
