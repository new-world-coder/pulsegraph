package com.pulsegraph.guardian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.compliance.AgentIdentity;
import com.pulsegraph.compliance.AutonomyMode;
import com.pulsegraph.compliance.DataClassification;
import com.pulsegraph.compliance.PolicyDecision;
import com.pulsegraph.compliance.RiskTier;
import com.pulsegraph.guardian.store.AgentRegistry;
import com.pulsegraph.guardian.store.KillSwitch;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

  private AgentRegistry registry;
  private KillSwitch killSwitch;
  private PolicyEngine engine;

  @BeforeEach
  void setup() {
    registry = new AgentRegistry();
    killSwitch = new KillSwitch();
    engine = new PolicyEngine(registry, killSwitch);
  }

  private AgentIdentity agent(AutonomyMode mode, DataClassification clearance) {
    return new AgentIdentity(
        "agent-1",
        "Test Agent",
        "owner",
        "test",
        List.of("restart-service"),
        "model",
        "v1",
        clearance,
        RiskTier.HIGH,
        mode,
        "active",
        Instant.now());
  }

  private ActionRequest action(String tool, String env, RiskTier risk, boolean destructive) {
    return new ActionRequest(
        "agent-1", "restart", tool, "checkout", env, risk, false, destructive,
        DataClassification.INTERNAL, "inc-1", "because");
  }

  @Test
  void unknownAgentIsDenied() {
    PolicyDecision d = engine.evaluate(action("restart-service", "prod", RiskTier.LOW, false));
    assertEquals(PolicyDecision.Effect.DENY, d.effect());
  }

  @Test
  void unapprovedToolIsDenied() {
    registry.register(agent(AutonomyMode.EXECUTE, DataClassification.RESTRICTED));
    PolicyDecision d = engine.evaluate(action("delete-database", "dev", RiskTier.LOW, false));
    assertEquals(PolicyDecision.Effect.DENY, d.effect());
  }

  @Test
  void killSwitchDenies() {
    registry.register(agent(AutonomyMode.EXECUTE, DataClassification.RESTRICTED));
    killSwitch.engageGlobal("incident");
    PolicyDecision d = engine.evaluate(action("restart-service", "dev", RiskTier.LOW, false));
    assertEquals(PolicyDecision.Effect.DENY, d.effect());
  }

  @Test
  void recommendAgentAlwaysRequiresApproval() {
    registry.register(agent(AutonomyMode.RECOMMEND, DataClassification.RESTRICTED));
    PolicyDecision d = engine.evaluate(action("restart-service", "dev", RiskTier.LOW, false));
    assertEquals(PolicyDecision.Effect.REQUIRE_APPROVAL, d.effect());
  }

  @Test
  void executeAgentAllowsLowRiskNonProd() {
    registry.register(agent(AutonomyMode.EXECUTE, DataClassification.RESTRICTED));
    PolicyDecision d = engine.evaluate(action("restart-service", "dev", RiskTier.LOW, false));
    assertEquals(PolicyDecision.Effect.ALLOW, d.effect());
  }

  @Test
  void executeAgentRequiresApprovalForDestructiveProd() {
    registry.register(agent(AutonomyMode.EXECUTE, DataClassification.RESTRICTED));
    PolicyDecision d = engine.evaluate(action("restart-service", "prod", RiskTier.LOW, true));
    assertEquals(PolicyDecision.Effect.REQUIRE_APPROVAL, d.effect());
  }

  @Test
  void criticalActionNeedsFourEyes() {
    registry.register(agent(AutonomyMode.EXECUTE, DataClassification.RESTRICTED));
    PolicyDecision d = engine.evaluate(action("restart-service", "prod", RiskTier.CRITICAL, true));
    assertEquals(PolicyDecision.Effect.REQUIRE_APPROVAL, d.effect());
    assertEquals(2, d.requiredApprovals());
  }

  @Test
  void dataClassificationBeyondClearanceIsDenied() {
    registry.register(agent(AutonomyMode.EXECUTE, DataClassification.INTERNAL));
    ActionRequest req =
        new ActionRequest(
            "agent-1", "restart", "restart-service", "checkout", "dev", RiskTier.LOW, false, false,
            DataClassification.RESTRICTED, "inc-1", "because");
    PolicyDecision d = engine.evaluate(req);
    assertEquals(PolicyDecision.Effect.DENY, d.effect());
    assertTrue(d.reason().toLowerCase().contains("data"));
  }
}
