package com.pulsegraph.warroom;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = "com.pulsegraph.warroom")
public class WarRoomApplication {

  public static void main(String[] args) {
    SpringApplication.run(WarRoomApplication.class, args);
  }

  @Bean
  GovernancePort governancePort(@Value("${pulsegraph.warroom.guardian-url:}") String guardianUrl) {
    if (guardianUrl == null || guardianUrl.isBlank()) {
      return InProcessGovernancePort.createSeeded();
    }
    return new HttpGovernancePort(guardianUrl);
  }

  @Bean
  WarRoomManager warRoomManager(GovernancePort port) {
    return new WarRoomManager(port);
  }

  @RestController
  static class HealthController {
    @GetMapping("/healthz")
    Map<String, Object> healthz() {
      return Map.of(
          "status", "ok",
          "service", "war-room",
          "executor", "in-memory-fleet",
          "liveCluster", false);
    }
  }
}
