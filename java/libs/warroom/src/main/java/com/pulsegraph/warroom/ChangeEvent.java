package com.pulsegraph.warroom;

public record ChangeEvent(String timestamp, String service, String kind, String summary) {}
