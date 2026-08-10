package com.pulsegraph.guardian.store;

import com.pulsegraph.compliance.AgentIdentity;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory agent registry (pilot-grade). Every agent that participates in investigation or
 * remediation must be registered here before the policy engine will authorise any action.
 */
@Component
public class AgentRegistry {

  private final ConcurrentMap<String, AgentIdentity> agents = new ConcurrentHashMap<>();

  public AgentIdentity register(AgentIdentity agent) {
    agents.put(agent.id(), agent);
    return agent;
  }

  public Optional<AgentIdentity> find(String id) {
    return Optional.ofNullable(agents.get(id));
  }

  public Collection<AgentIdentity> list() {
    return agents.values();
  }
}
