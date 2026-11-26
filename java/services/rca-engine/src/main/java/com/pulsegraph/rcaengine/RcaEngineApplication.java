package com.pulsegraph.rcaengine;

import java.util.List;
import java.util.Map;
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

/**
 * Java RCA orchestrator.
 *
 * <p>Rule/graph ranking runs in-process; ML / Isolation Forest / LLM calls the Python sidecar
 * at {@code pulsegraph.python-sidecar-url} (existing {@code services/rca-engine}).
 */
@SpringBootApplication
public class RcaEngineApplication {
  public static void main(String[] args) {
    SpringApplication.run(RcaEngineApplication.class, args);
  }

  @RestController
  static class RcaController {
    private static final Logger log = LoggerFactory.getLogger(RcaController.class);
    private final RestClient sidecar;
    private final String sidecarUrl;

    RcaController(@Value("${pulsegraph.python-sidecar-url:http://localhost:8096}") String sidecarUrl) {
      this.sidecarUrl = sidecarUrl;
      this.sidecar = RestClient.builder().baseUrl(sidecarUrl).build();
    }

    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      boolean sidecarOk = false;
      try {
        sidecar.get().uri("/healthz").retrieve().toBodilessEntity();
        sidecarOk = true;
      } catch (Exception ignored) {
        // sidecar optional at boot
      }
      return Map.of(
          "status",
          "ok",
          "service",
          "rca-engine",
          "stack",
          "spring-boot",
          "python_sidecar_url",
          sidecarUrl,
          "python_sidecar_ok",
          sidecarOk);
    }

    @PostMapping(path = "/v1/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> analyze(@RequestBody Map<String, Object> request) {
      // Phase-1 Java rules
      List<Map<String, Object>> javaHypotheses =
          List.of(
              Map.of(
                  "id",
                  "java-rule-deploy",
                  "title",
                  "Recent deploy correlated with error spike (rule)",
                  "confidence",
                  0.55,
                  "source",
                  "java-rules"));

      Object sidecarResult = null;
      try {
        sidecarResult =
            sidecar
                .post()
                .uri("/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Object.class);
        log.info("python sidecar analyze ok");
      } catch (Exception e) {
        log.warn("python sidecar unavailable: {}", e.toString());
      }

      return ResponseEntity.ok(
          Map.of(
              "incident_id",
              request.getOrDefault("incident_id", "unknown"),
              "java_hypotheses",
              javaHypotheses,
              "python_sidecar",
              sidecarResult == null ? Map.of("status", "unavailable") : sidecarResult));
    }
  }
}
