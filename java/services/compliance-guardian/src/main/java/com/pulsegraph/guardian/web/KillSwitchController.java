package com.pulsegraph.guardian.web;

import com.pulsegraph.guardian.GovernanceService;
import com.pulsegraph.guardian.store.KillSwitch;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operational-safety kill switch and per-agent circuit breaker. */
@RestController
@RequestMapping("/v1/kill-switch")
public class KillSwitchController {

  private final GovernanceService governance;
  private final KillSwitch killSwitch;

  public KillSwitchController(GovernanceService governance, KillSwitch killSwitch) {
    this.governance = governance;
    this.killSwitch = killSwitch;
  }

  @GetMapping
  public Map<String, Object> status() {
    return Map.of(
        "globalHalted", killSwitch.isGlobalHalted(),
        "reason", killSwitch.reason(),
        "pausedAgents", killSwitch.pausedAgents());
  }

  @PostMapping("/engage")
  public Map<String, Object> engage(@RequestBody SwitchRequest req) {
    governance.engageGlobalKill(actor(req), req.reason());
    return status();
  }

  @PostMapping("/release")
  public Map<String, Object> release(@RequestBody SwitchRequest req) {
    governance.releaseGlobalKill(actor(req));
    return status();
  }

  @PostMapping("/pause")
  public Map<String, Object> pause(@RequestBody SwitchRequest req) {
    governance.pauseAgent(actor(req), req.agentId(), req.reason());
    return status();
  }

  @PostMapping("/resume")
  public Map<String, Object> resume(@RequestBody SwitchRequest req) {
    governance.resumeAgent(actor(req), req.agentId());
    return status();
  }

  private static String actor(SwitchRequest req) {
    return (req.actor() == null || req.actor().isBlank()) ? "operator" : req.actor();
  }

  public record SwitchRequest(String actor, String agentId, String reason) {}
}
