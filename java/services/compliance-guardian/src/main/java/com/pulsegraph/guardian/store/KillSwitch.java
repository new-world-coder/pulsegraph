package com.pulsegraph.guardian.store;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Operational safety control. A global kill switch halts all agent actions immediately; individual
 * agents can also be paused (circuit breaker). Provides the human "stop" required by EU AI Act
 * Art.14 and DORA operational-resilience expectations.
 */
@Component
public class KillSwitch {

  private final AtomicBoolean globalHalt = new AtomicBoolean(false);
  private final Set<String> pausedAgents = ConcurrentHashMap.newKeySet();
  private volatile String reason = "";

  public void engageGlobal(String reason) {
    this.reason = reason == null ? "" : reason;
    globalHalt.set(true);
  }

  public void releaseGlobal() {
    globalHalt.set(false);
    this.reason = "";
  }

  public void pauseAgent(String agentId) {
    pausedAgents.add(agentId);
  }

  public void resumeAgent(String agentId) {
    pausedAgents.remove(agentId);
  }

  public boolean isGlobalHalted() {
    return globalHalt.get();
  }

  public boolean isAgentPaused(String agentId) {
    return pausedAgents.contains(agentId);
  }

  /** True if the given agent is blocked either by the global halt or by an agent-level pause. */
  public boolean isTripped(String agentId) {
    return globalHalt.get() || pausedAgents.contains(agentId);
  }

  public String reason() {
    return reason;
  }

  public Set<String> pausedAgents() {
    return Set.copyOf(pausedAgents);
  }
}
