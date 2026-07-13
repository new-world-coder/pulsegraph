package com.pulsegraph.correlator;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class CorrelatorApplication {
  public static void main(String[] args) {
    SpringApplication.run(CorrelatorApplication.class, args);
  }

  @RestController
  static class HealthController {
    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      return Map.of("status", "ok", "service", "correlator", "stack", "spring-boot");
    }
  }
}
