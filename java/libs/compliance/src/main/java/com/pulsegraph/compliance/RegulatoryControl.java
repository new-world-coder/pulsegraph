package com.pulsegraph.compliance;

/**
 * Catalogue of regulatory controls that individual policy decisions and audit events can cite. Each
 * value links a framework, a clause reference, and a short description so that evidence packs can be
 * traced back to a specific obligation.
 *
 * <p>These references support regulatory alignment and auditability; they are not a substitute for
 * institution-specific legal and supervisory review.
 */
public enum RegulatoryControl {
  // EU AI Act
  EU_AI_ACT_HUMAN_OVERSIGHT(RegulatoryFramework.EU_AI_ACT, "Art.14", "Effective human oversight of high-risk AI"),
  EU_AI_ACT_TRACEABILITY(RegulatoryFramework.EU_AI_ACT, "Art.12", "Automatic logging and traceability of events"),
  EU_AI_ACT_TRANSPARENCY(RegulatoryFramework.EU_AI_ACT, "Art.13", "Transparency and provision of information"),
  EU_AI_ACT_TECH_DOCS(RegulatoryFramework.EU_AI_ACT, "Art.11", "Technical documentation of the AI system"),

  // DORA
  DORA_ICT_RISK_GOVERNANCE(RegulatoryFramework.DORA, "Art.5", "ICT risk management governance"),
  DORA_INCIDENT_REPORTING(RegulatoryFramework.DORA, "Art.17-19", "ICT incident classification and reporting"),
  DORA_RESILIENCE_TESTING(RegulatoryFramework.DORA, "Art.24-27", "Digital operational-resilience testing"),
  DORA_THIRD_PARTY_RISK(RegulatoryFramework.DORA, "Art.28-30", "ICT third-party and concentration risk"),

  // MAS FEAT / Veritas
  MAS_FEAT_ACCOUNTABILITY(RegulatoryFramework.MAS_FEAT, "Accountability", "Accountability for AI-driven decisions"),
  MAS_FEAT_TRANSPARENCY(RegulatoryFramework.MAS_FEAT, "Transparency", "Transparency of AI-driven decisions"),
  MAS_VERITAS_FAIRNESS(RegulatoryFramework.MAS_VERITAS, "Fairness", "Documented fairness assessment"),

  // MAS MindForge / proposed AI risk-management guidance
  MAS_MINDFORGE_LIFECYCLE(RegulatoryFramework.MAS_MINDFORGE, "Lifecycle", "AI lifecycle controls and model inventory"),
  MAS_MINDFORGE_OVERSIGHT(RegulatoryFramework.MAS_MINDFORGE, "Oversight", "Board and senior-management oversight"),

  // NIST AI RMF / ISO 42001
  NIST_GOVERN(RegulatoryFramework.NIST_AI_RMF, "GOVERN", "Governance of AI risk"),
  NIST_MANAGE(RegulatoryFramework.NIST_AI_RMF, "MANAGE", "Manage and respond to AI risk"),
  ISO_42001_AIMS(RegulatoryFramework.ISO_42001, "AIMS", "Auditable AI management-system evidence");

  private final RegulatoryFramework framework;
  private final String clause;
  private final String description;

  RegulatoryControl(RegulatoryFramework framework, String clause, String description) {
    this.framework = framework;
    this.clause = clause;
    this.description = description;
  }

  public RegulatoryFramework framework() {
    return framework;
  }

  public String clause() {
    return clause;
  }

  public String description() {
    return description;
  }
}
