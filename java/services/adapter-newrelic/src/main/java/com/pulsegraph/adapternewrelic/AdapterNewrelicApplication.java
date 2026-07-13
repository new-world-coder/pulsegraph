package com.pulsegraph.adapternewrelic;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class AdapterNewrelicApplication {
  public static void main(String[] args) {
    SpringApplication.run(AdapterNewrelicApplication.class, args);
  }

  @RestController
  static class HealthController {
    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      return Map.of("status", "ok", "service", "adapter-newrelic", "stack", "spring-boot");
    }
  }
}
