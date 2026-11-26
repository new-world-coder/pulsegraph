package com.pulsegraph.guardian.store;

import com.pulsegraph.compliance.AuditChain;
import com.pulsegraph.compliance.AuditRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Append-only, hash-chained audit log (pilot-grade, in-memory). Appends are synchronised so the
 * sequence number and previous-hash pointer stay consistent under concurrency. A production
 * deployment would persist these records to WORM storage or an append-only ledger.
 */
@Component
public class AuditLog {

  private final List<AuditRecord> records = new ArrayList<>();

  public synchronized AuditRecord append(
      String agentId, String actorType, String actor, String eventType, Map<String, Object> attrs) {
    long seq = records.size();
    String prevHash = records.isEmpty() ? AuditChain.GENESIS : records.get(records.size() - 1).hash();
    AuditRecord record = AuditChain.append(seq, prevHash, agentId, actorType, actor, eventType, attrs);
    records.add(record);
    return record;
  }

  public synchronized List<AuditRecord> all() {
    return List.copyOf(records);
  }

  public synchronized List<AuditRecord> byIncident(String incidentId) {
    List<AuditRecord> out = new ArrayList<>();
    for (AuditRecord r : records) {
      Object inc = r.attributes().get("incidentId");
      if (inc != null && inc.toString().equals(incidentId)) {
        out.add(r);
      }
    }
    return out;
  }

  /**
   * Verifies both per-record hashes and the linkage between consecutive records. Returns the index
   * of the first broken record, or -1 if the whole chain is intact.
   */
  public synchronized int verify() {
    String expectedPrev = AuditChain.GENESIS;
    for (int i = 0; i < records.size(); i++) {
      AuditRecord r = records.get(i);
      if (!r.prevHash().equals(expectedPrev) || !AuditChain.verifyRecord(r)) {
        return i;
      }
      expectedPrev = r.hash();
    }
    return -1;
  }

  public synchronized int size() {
    return records.size();
  }
}
