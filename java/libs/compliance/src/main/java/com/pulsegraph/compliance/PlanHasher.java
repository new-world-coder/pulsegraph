package com.pulsegraph.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Canonical SHA-256 of a {@link RemediationPlan}. The same plan always hashes the same. */
public final class PlanHasher {

  private PlanHasher() {}

  public static String hash(RemediationPlan plan) {
    if (plan == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(nz(plan.operation()))
        .append('|')
        .append(nz(plan.targetService()))
        .append('|')
        .append(nz(plan.expectedEffect()))
        .append('|')
        .append(nz(plan.rollback()))
        .append('|');
    Map<String, String> sorted = new TreeMap<>(plan.parameters());
    for (Map.Entry<String, String> e : sorted.entrySet()) {
      sb.append(e.getKey()).append('=').append(nz(e.getValue())).append(';');
    }
    sb.append('|');
    List<String> evidence = new ArrayList<>(plan.evidenceIds());
    evidence.sort(String::compareTo);
    for (String id : evidence) {
      sb.append(nz(id)).append(',');
    }
    return sha256(sb.toString());
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
