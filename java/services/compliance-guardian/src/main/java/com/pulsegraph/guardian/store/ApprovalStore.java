package com.pulsegraph.guardian.store;

import com.pulsegraph.guardian.model.ApprovalRequest;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** In-memory store of human-in-the-loop approval requests (pilot-grade). */
@Component
public class ApprovalStore {

  private final ConcurrentMap<String, ApprovalRequest> requests = new ConcurrentHashMap<>();

  public ApprovalRequest save(ApprovalRequest request) {
    requests.put(request.id(), request);
    return request;
  }

  public Optional<ApprovalRequest> find(String id) {
    return Optional.ofNullable(requests.get(id));
  }

  public Collection<ApprovalRequest> list() {
    return requests.values();
  }
}
