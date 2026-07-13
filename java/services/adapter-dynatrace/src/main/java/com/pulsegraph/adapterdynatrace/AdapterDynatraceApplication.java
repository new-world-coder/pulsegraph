package com.pulsegraph.adapterdynatrace;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class AdapterDynatraceApplication {
  public static void main(String[] args) {
    SpringApplication.run(AdapterDynatraceApplication.class, args);
  }

  @RestController
  static class HealthController {
    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      return Map.of("status", "ok", "service", "adapter-dynatrace", "stack", "spring-boot");
    }
  }
}
