package com.pulsegraph.warroom;

public record Finding(
    String id,
    String agent,
    String service,
    String timestamp,
    String name,
    String value,
    String baseline,
    boolean instructionStripped,
    boolean secretRedacted) {}
