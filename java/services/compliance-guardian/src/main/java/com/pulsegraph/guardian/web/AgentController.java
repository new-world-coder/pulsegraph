package com.pulsegraph.guardian.web;

import com.pulsegraph.compliance.AgentIdentity;
import com.pulsegraph.compliance.AutonomyMode;
import com.pulsegraph.compliance.DataClassification;
import com.pulsegraph.compliance.RiskTier;
import com.pulsegraph.guardian.GovernanceService;
import com.pulsegraph.guardian.store.AgentRegistry;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agent identity and registry API. */
@RestController
@RequestMapping("/v1/agents")
public class AgentController {

  private final GovernanceService governance;
  private final AgentRegistry registry;

  public AgentController(GovernanceService governance, AgentRegistry registry) {
    this.governance = governance;
    this.registry = registry;
  }

  @PostMapping
  public AgentIdentity register(@RequestBody RegisterRequest req) {
    String id = (req.id() == null || req.id().isBlank()) ? "agent-" + UUID.randomUUID() : req.id();
    AgentIdentity agent =
        new AgentIdentity(
            id,
            req.name(),
            req.owner(),
            req.purpose(),
            req.approvedTools() == null ? List.of() : req.approvedTools(),
            req.model(),
            req.modelVersion(),
            req.maxDataClassification() == null
                ? DataClassification.INTERNAL
                : req.maxDataClassification(),
            req.riskTier() == null ? RiskTier.MEDIUM : req.riskTier(),
            req.maxAutonomy() == null ? AutonomyMode.RECOMMEND : req.maxAutonomy(),
            req.deploymentStatus() == null ? "registered" : req.deploymentStatus(),
            Instant.now());
    return governance.registerAgent(agent);
  }

  @GetMapping
  public Collection<AgentIdentity> list() {
    return registry.list();
  }

  @GetMapping("/{id}")
  public ResponseEntity<AgentIdentity> get(@PathVariable String id) {
    return registry.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  public record RegisterRequest(
      String id,
      String name,
      String owner,
      String purpose,
      List<String> approvedTools,
      String model,
      String modelVersion,
      DataClassification maxDataClassification,
      RiskTier riskTier,
      AutonomyMode maxAutonomy,
      String deploymentStatus) {}
}
