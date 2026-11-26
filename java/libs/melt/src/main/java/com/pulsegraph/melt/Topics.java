package com.pulsegraph.melt;

public final class Topics {
  public static final String RAW_MELT = "raw.melt";
  public static final String RAW_MELT_DLQ = "raw.melt.dlq";
  public static final String NORM_MELT = "norm.melt";
  public static final String NORM_MELT_DLQ = "norm.melt.dlq";
  public static final String CORR_EVENTS = "corr.events";
  public static final String SNAPSHOT_COMMANDS = "snapshot.commands";
  public static final String SNAPSHOT_REFS = "snapshot.refs";
  public static final String RCA_RESULTS = "rca.results";
  public static final String TOPOLOGY_CHANGES = "topology.changes";
  public static final String INCIDENTS = "incidents";

  private Topics() {}
}
