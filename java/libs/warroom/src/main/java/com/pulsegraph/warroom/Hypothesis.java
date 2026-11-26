package com.pulsegraph.warroom;

import java.util.List;

public record Hypothesis(
    String id,
    String title,
    double confidence,
    Cause cause,
    List<String> supports,
    List<String> contradicts,
    int round) {

  public Hypothesis {
    supports = supports == null ? List.of() : List.copyOf(supports);
    contradicts = contradicts == null ? List.of() : List.copyOf(contradicts);
  }
}
