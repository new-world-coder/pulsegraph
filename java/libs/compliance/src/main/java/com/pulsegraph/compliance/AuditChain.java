package com.pulsegraph.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds and verifies the tamper-evident audit hash chain. Callers keep the previous record's hash
 * and pass it to {@link #append}; the returned record contains a SHA-256 hash over a canonical
 * serialisation of its fields plus the previous hash.
 */
public final class AuditChain {

  /** Hash used as the predecessor of the first record. */
  public static final String GENESIS = "0".repeat(64);

  private AuditChain() {}

  public static AuditRecord append(
      long sequence,
      String prevHash,
      String agentId,
      String actorType,
      String actor,
      String eventType,
      Map<String, Object> attributes) {
    Instant ts = Instant.now();
    Map<String, Object> safe = sanitize(attributes);
    String canonical =
        canonical(sequence, ts, agentId, actorType, actor, eventType, safe, prevHash);
    return new AuditRecord(
        sequence, ts, agentId, actorType, actor, eventType, safe, prevHash, sha256(canonical));
  }

  /** Records forbid null keys/values; replace any null value with an empty string. */
  private static Map<String, Object> sanitize(Map<String, Object> attributes) {
    if (attributes == null) {
      return Map.of();
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : attributes.entrySet()) {
      if (e.getKey() == null) {
        continue;
      }
      out.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
    }
    return out;
  }

  /** Recomputes the hash of a single record and checks it matches the stored hash. */
  public static boolean verifyRecord(AuditRecord r) {
    String canonical =
        canonical(
            r.sequence(),
            r.timestamp(),
            r.agentId(),
            r.actorType(),
            r.actor(),
            r.eventType(),
            r.attributes(),
            r.prevHash());
    return sha256(canonical).equals(r.hash());
  }

  private static String canonical(
      long seq,
      Instant ts,
      String agentId,
      String actorType,
      String actor,
      String eventType,
      Map<String, Object> attrs,
      String prevHash) {
    StringBuilder sb = new StringBuilder();
    sb.append(seq)
        .append('|')
        .append(ts.toString())
        .append('|')
        .append(nz(agentId))
        .append('|')
        .append(nz(actorType))
        .append('|')
        .append(nz(actor))
        .append('|')
        .append(nz(eventType))
        .append('|');
    Map<String, Object> sorted = new TreeMap<>(attrs == null ? Map.of() : attrs);
    for (Map.Entry<String, Object> e : sorted.entrySet()) {
      sb.append(e.getKey()).append('=').append(String.valueOf(e.getValue())).append(';');
    }
    sb.append('|').append(nz(prevHash));
    return sb.toString();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static String sha256(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
