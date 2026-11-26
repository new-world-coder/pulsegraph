package com.pulsegraph.guardian;

import com.pulsegraph.compliance.AgentIdentity;
import com.pulsegraph.compliance.AutonomyMode;
import com.pulsegraph.compliance.DataClassification;
import com.pulsegraph.compliance.RiskTier;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registers PulseGraph's built-in RCA agent on startup so the War Room and evidence-pack demos have
 * a governed identity to reference. Disable with {@code pulsegraph.guardian.seed=false}.
 */
@Component
@ConditionalOnProperty(name = "pulsegraph.guardian.seed", havingValue = "true", matchIfMissing = true)
public class SeedData implements CommandLineRunner {

  private final GovernanceService governance;

  public SeedData(GovernanceService governance) {
    this.governance = governance;
  }

  @Override
  public void run(String... args) {
    governance.registerAgent(
        new AgentIdentity(
            "agent-rca-primary",
            "PulseGraph RCA Investigator",
            "sre-platform-team",
            "Correlate MELT signals and Context Graph paths to propose incident root cause.",
            List.of("query-metrics", "query-logs", "query-traces", "read-context-graph", "read-freeze"),
            "gpt-class-llm",
            "2026.07",
            DataClassification.CONFIDENTIAL,
            RiskTier.HIGH,
            AutonomyMode.RECOMMEND,
            "active",
            Instant.now()));
    governance.registerAgent(
        new AgentIdentity(
            "agent-remediation",
            "PulseGraph Remediation",
            "sre-platform-team",
            "Propose a bounded operation from a confirmed hypothesis.",
            List.of(
                "set-connection-pool", "rollback-release", "scale-replicas", "restart-pod"),
            "pulsegraph-deterministic",
            "2026.11",
            DataClassification.CONFIDENTIAL,
            RiskTier.HIGH,
            AutonomyMode.REQUIRE_APPROVAL,
            "active",
            Instant.now()));
  }
}
