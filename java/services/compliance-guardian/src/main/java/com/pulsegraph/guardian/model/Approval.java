package com.pulsegraph.guardian.model;

import java.time.Instant;

/** A single human sign-off (or rejection) recorded against an {@link ApprovalRequest}. */
public record Approval(String approver, String role, boolean granted, String comment, Instant at) {}
