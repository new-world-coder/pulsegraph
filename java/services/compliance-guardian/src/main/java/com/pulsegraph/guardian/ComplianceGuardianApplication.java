package com.pulsegraph.guardian;

import com.pulsegraph.compliance.ActionPolicy;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compliant Agent control plane. Governs every autonomous or semi-autonomous agent action in
 * PulseGraph: agent registry, autonomy policy evaluation, human approvals (dual / four-eyes),
 * immutable hash-chained audit trail, kill switch / circuit breaker, and regulatory evidence packs.
 */
@SpringBootApplication
public class ComplianceGuardianApplication {
  public static void main(String[] args) {
    SpringApplication.run(ComplianceGuardianApplication.class, args);
  }

  @Bean
  ActionPolicy actionPolicy() {
    return ActionPolicy.loadDefault();
  }

  @RestController
  static class HealthController {
    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      return Map.of("status", "ok", "service", "compliance-guardian", "stack", "spring-boot");
    }
  }
}
