package com.pulsegraph.ingestgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsegraph.melt.Topics;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class IngestGatewayApplication {
  public static void main(String[] args) {
    SpringApplication.run(IngestGatewayApplication.class, args);
  }

  @RestController
  static class IngestController {
    private static final Logger log = LoggerFactory.getLogger(IngestController.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    IngestController(
        KafkaTemplate<String, String> kafka,
        ObjectMapper mapper,
        @Value("${pulsegraph.raw-topic:" + Topics.RAW_MELT + "}") String topic) {
      this.kafka = kafka;
      this.mapper = mapper;
      this.topic = topic;
    }

    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      return Map.of("status", "ok", "service", "ingest-gateway", "topic", topic);
    }

    @PostMapping({"/v1/ingest", "/ingest"})
    ResponseEntity<?> ingest(@RequestBody JsonNode body) throws Exception {
      if (body == null || body.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "empty body"));
      }
      var root = body.isObject() ? body.deepCopy() : mapper.createObjectNode();
      if (root instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
        if (!obj.hasNonNull("id")) {
          obj.put("id", UUID.randomUUID().toString());
        }
        if (!obj.hasNonNull("tenant_id")) {
          obj.put("tenant_id", "default");
        }
        obj.put("ingested_at_unix_ns", Instant.now().toEpochMilli() * 1_000_000L);
      }
      String id = root.path("id").asText("unknown");
      String tenant = root.path("tenant_id").asText("default");
      String service = root.path("service_name").asText("unknown");
      String key = tenant + "|" + service;
      String payload = mapper.writeValueAsString(root);
      kafka.send(topic, key, payload);
      log.info("accepted envelope_id={} service={}", id, service);
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(Map.of("accepted", true, "envelope_id", id, "topic", topic));
    }
  }
}
