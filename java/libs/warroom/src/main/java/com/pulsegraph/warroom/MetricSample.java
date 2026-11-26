package com.pulsegraph.warroom;

public record MetricSample(
    String name, double value, double baseline, String timestamp, String service) {}
