package com.pulsegraph.compliance;

/**
 * Data sensitivity an agent is cleared to handle. Used for data-governance checks that prevent
 * restricted banking data from reaching unapproved models (DORA / MAS data-governance).
 */
public enum DataClassification {
  PUBLIC,
  INTERNAL,
  CONFIDENTIAL,
  RESTRICTED
}
