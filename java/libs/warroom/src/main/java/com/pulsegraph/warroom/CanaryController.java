package com.pulsegraph.warroom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Canary controller over an in-memory fleet of five instances. It does not talk to a cluster.
 * Analysis is assumed to have read all five. Execution touches index 0 only, and a repeated
 * idempotency key does not touch it again.
 */
public final class CanaryController {

  public static final int FLEET_SIZE = 5;
  public static final int CANARY_INDEX = 0;

  private final ConcurrentHashMap<String, CanaryResult> byKey = new ConcurrentHashMap<>();
  private final AtomicInteger applications = new AtomicInteger();

  public CanaryResult execute(String idempotencyKey, boolean permitted, ExpectedSignal signal) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return new CanaryResult("", "REFUSED", false, false, FLEET_SIZE, CANARY_INDEX, "Missing idempotency key.");
    }
    CanaryResult prior = byKey.get(idempotencyKey);
    if (prior != null) {
      return prior.asReplay();
    }
    if (!permitted) {
      return new CanaryResult(
          idempotencyKey,
          "REFUSED",
          false,
          false,
          FLEET_SIZE,
          CANARY_INDEX,
          "Gate did not permit execution. No instance was changed.");
    }
    ExpectedSignal outcome = signal == null ? ExpectedSignal.IMPROVED : signal;
    applications.incrementAndGet();
    CanaryResult result =
        switch (outcome) {
          case IMPROVED ->
              new CanaryResult(
                  idempotencyKey,
                  "PROMOTED",
                  true,
                  false,
                  FLEET_SIZE,
                  CANARY_INDEX,
                  "Canary instance 1 of 5 improved against its peers. Change promoted.");
          case FLAT, WORSE ->
              new CanaryResult(
                  idempotencyKey,
                  "ROLLED_BACK",
                  true,
                  false,
                  FLEET_SIZE,
                  CANARY_INDEX,
                  "Canary instance 1 of 5 was " + outcome.name().toLowerCase()
                      + " against its peers. Change rolled back. The other four kept serving.");
        };
    CanaryResult raced = byKey.putIfAbsent(idempotencyKey, result);
    if (raced != null) {
      return raced.asReplay();
    }
    return result;
  }

  public int applications() {
    return applications.get();
  }
}
