package com.pulsegraph.adapterotel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class AdapterOtelApplication {
  public static void main(String[] args) {
    SpringApplication.run(AdapterOtelApplication.class, args);
  }

  @RestController
  static class OtlpController {
    private static final Logger log = LoggerFactory.getLogger(OtlpController.class);
    private final RestClient gateway;
    private final ObjectMapper mapper;
    private final String tenantId;

    OtlpController(
        ObjectMapper mapper,
        @Value("${pulsegraph.ingest-gateway-url:http://localhost:8081}") String gatewayUrl,
        @Value("${pulsegraph.tenant-id:default}") String tenantId) {
      this.mapper = mapper;
      this.tenantId = tenantId;
      this.gateway = RestClient.builder().baseUrl(gatewayUrl).build();
    }

    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      return Map.of("status", "ok", "service", "adapter-otel");
    }

    @PostMapping(path = "/v1/traces", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> traces(@RequestBody JsonNode body) {
      return forward("trace", body);
    }

    @PostMapping(path = "/v1/metrics", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> metrics(@RequestBody JsonNode body) {
      return forward("metric", body);
    }

    @PostMapping(path = "/v1/logs", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> logs(@RequestBody JsonNode body) {
      return forward("log", body);
    }

    private ResponseEntity<?> forward(String signal, JsonNode body) {
      ObjectNode envelope = mapper.createObjectNode();
      envelope.put("id", UUID.randomUUID().toString());
      envelope.put("tenant_id", tenantId);
      envelope.put("source_vendor", "otel");
      envelope.put("signal_type", signal);
      envelope.put("timestamp_unix_ns", Instant.now().toEpochMilli() * 1_000_000L);
      envelope.put("service_name", extractService(body));
      envelope.put("environment", "unknown");
      envelope.set("payload", body);
      try {
        gateway.post().uri("/v1/ingest").contentType(MediaType.APPLICATION_JSON).body(envelope).retrieve().toBodilessEntity();
        log.info("otlp accepted signal={} service={}", signal, envelope.get("service_name").asText());
        return ResponseEntity.ok(Map.of());
      } catch (Exception e) {
        log.error("gateway forward failed", e);
        return ResponseEntity.status(502).body(Map.of("error", "ingest gateway unavailable"));
      }
    }

    private String extractService(JsonNode body) {
      // Best-effort: resourceSpans[0].resource.attributes service.name
      JsonNode attrs =
          body.path("resourceSpans").path(0).path("resource").path("attributes");
      if (attrs.isArray()) {
        for (JsonNode a : attrs) {
          if ("service.name".equals(a.path("key").asText())) {
            return a.path("value").path("stringValue").asText("unknown");
          }
        }
      }
      return "unknown";
    }
  }
}
