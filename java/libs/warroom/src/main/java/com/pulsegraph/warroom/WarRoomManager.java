package com.pulsegraph.warroom;

import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.compliance.DataClassification;
import com.pulsegraph.compliance.PlanHasher;
import com.pulsegraph.compliance.RemediationPlan;
import com.pulsegraph.compliance.RiskTier;
import com.pulsegraph.warroom.GovernancePort.Gate;
import com.pulsegraph.warroom.GovernancePort.Permit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Hub of the war room. Holds the incident, fans specialists out in parallel, loops when confidence
 * is low, and will not execute a proposal. Execution is a separate canary step after the gate.
 */
public final class WarRoomManager {

  public static final String MODEL = "pulsegraph-deterministic";
  public static final String MODEL_VERSION = "2026.11";
  public static final String REMEDIATION_AGENT = "agent-remediation";
  static final int MAX_ROUNDS = 2;

  private final GovernancePort port;
  private final CanaryController canary = new CanaryController();
  private final ExecutorService pool =
      Executors.newFixedThreadPool(
          4,
          runnable -> {
            Thread thread = new Thread(runnable, "war-room-analyst");
            thread.setDaemon(true);
            return thread;
          });
  private final ConcurrentMap<String, WarRoomView> rooms = new ConcurrentHashMap<>();

  public WarRoomManager(GovernancePort port) {
    this.port = port;
  }

  public WarRoomView open(Detection detection, EvidenceBundle evidence) {
    String service = detection.service() == null ? "unknown" : detection.service();
    EvidenceBundle bundle = evidence == null ? EvidenceBundle.empty(service) : evidence;
    if (bundle.focusService().equals("unknown") && detection.service() != null) {
      bundle =
          new EvidenceBundle(
              service,
              bundle.serviceSlice(),
              bundle.windowStart(),
              bundle.windowEnd(),
              bundle.metrics(),
              bundle.logs(),
              bundle.edges(),
              bundle.changes());
    }
    String id = "wr-" + UUID.randomUUID().toString().substring(0, 8);
    List<String> timeline = new ArrayList<>();
    timeline.add("Detection opened " + id + " for " + bundle.focusService());
    record(id, "DETECTION", Map.of("service", bundle.focusService(), "trigger", nz(detection.trigger())));
    WarRoomView view = investigate(id, detection.environment(), bundle, timeline, 1);
    rooms.put(id, view);
    return view;
  }

  public WarRoomView get(String id) {
    WarRoomView view = rooms.get(id);
    if (view == null) {
      throw new IllegalArgumentException("Unknown war room " + id);
    }
    return view;
  }

  public WarRoomView approve(String id, String approver) {
    WarRoomView current = get(id);
    if (current.approvalId() == null) {
      return current;
    }
    String who = approver == null || approver.isBlank() ? "oncall" : approver;
    boolean granted = port.decide(current.approvalId(), who, true);
    List<String> timeline = new ArrayList<>(current.timeline());
    timeline.add(
        granted
            ? "Human " + who + " approved plan " + current.planHash()
            : "Approval was not recorded");
    WarRoomView next = current.withPhase(granted ? "APPROVED" : current.phase(), timeline);
    rooms.put(id, next);
    return next;
  }

  public WarRoomView runCanary(String id, String planHash, String idempotencyKey, ExpectedSignal signal) {
    WarRoomView current = get(id);
    String key =
        idempotencyKey == null || idempotencyKey.isBlank()
            ? id + ":" + nz(current.planHash())
            : idempotencyKey;
    Permit permit = port.permit(current.approvalId(), planHash, key);
    boolean mayApply = permit.permitted() && !permit.replay();
    CanaryResult result = canary.execute(key, mayApply, signal);
    if (permit.replay() && !result.replay()) {
      result = result.asReplay();
    }
    List<String> timeline = new ArrayList<>(current.timeline());
    timeline.add(result.detail());
    String phase =
        switch (result.outcome()) {
          case "PROMOTED" -> "CANARY_PROMOTED";
          case "ROLLED_BACK" -> "CANARY_ROLLED_BACK";
          default -> "CANARY_REFUSED";
        };
    record(
        id,
        "CANARY",
        Map.of("outcome", result.outcome(), "replay", Boolean.toString(result.replay()), "planHash", nz(planHash)));
    record(
        id,
        "VERIFICATION",
        Map.of("outcome", result.outcome(), "fleetSize", Integer.toString(result.fleetSize())));
    WarRoomView next = current.withCanary(phase, timeline, result);
    rooms.put(id, next);
    return next;
  }

  public int canaryApplications() {
    return canary.applications();
  }

  private WarRoomView investigate(
      String id, String environment, EvidenceBundle bundle, List<String> timeline, int round) {
    record(id, "FAN_OUT", Map.of("round", Integer.toString(round), "service", bundle.focusService()));
    List<Finding> roundFindings = prefix(fanOut(bundle), round);
    timeline.add(
        "Round "
            + round
            + " collected "
            + roundFindings.size()
            + " findings from metrics, logs, topology, and deploy");
    record(
        id,
        "EVIDENCE",
        Map.of("round", Integer.toString(round), "findings", Integer.toString(roundFindings.size())));

    Hypothesis hypothesis = Synthesis.rank(roundFindings, bundle.focusService(), round);
    timeline.add(
        "Hypothesis "
            + hypothesis.id()
            + " confidence "
            + String.format("%.2f", hypothesis.confidence()));
    record(
        id,
        "HYPOTHESIS",
        Map.of(
            "hypothesisId", hypothesis.id(),
            "confidence", String.format("%.2f", hypothesis.confidence()),
            "cause", hypothesis.cause().name()));

    if (hypothesis.confidence() < Synthesis.THRESHOLD && round < MAX_ROUNDS) {
      String followUp = "Round " + (round + 1) + " restricted to " + bundle.focusService();
      timeline.add("Sufficiency failed. " + followUp);
      record(id, "SUFFICIENCY", Map.of("round", Integer.toString(round), "followUp", followUp));
      return investigate(id, environment, bundle.focusOnly(), timeline, round + 1);
    }

    RemediationPlan proposal = null;
    String planHash = null;
    String gateEffect = null;
    String gateReason = null;
    String approvalId = null;
    String phase = "INSUFFICIENT";
    String followUp = round > 1 ? "Asked a narrower question on round " + round : "";

    if (hypothesis.confidence() >= Synthesis.THRESHOLD && hypothesis.cause() != Cause.UNKNOWN) {
      proposal = Synthesis.propose(hypothesis, bundle.focusService());
      planHash = PlanHasher.hash(proposal);
      record(id, "PROPOSAL", Map.of("operation", proposal.operation(), "planHash", planHash));
      ActionRequest request =
          new ActionRequest(
              REMEDIATION_AGENT,
              proposal.operation(),
              proposal.operation(),
              proposal.targetService(),
              environment == null ? "prod" : environment,
              RiskTier.HIGH,
              true,
              false,
              DataClassification.INTERNAL,
              id,
              hypothesis.title(),
              proposal,
              planHash,
              bundle.boundary());
      Gate gate = port.authorize(request);
      gateEffect = gate.effect();
      gateReason = gate.reason();
      approvalId = gate.approvalId();
      planHash = gate.planHash() == null || gate.planHash().isBlank() ? planHash : gate.planHash();
      phase =
          switch (gate.effect() == null ? "" : gate.effect()) {
            case "REQUIRE_APPROVAL" -> "AWAITING_APPROVAL";
            case "ALLOW" -> "APPROVED";
            default -> "DENIED";
          };
      timeline.add("Gate " + gate.effect() + ". " + gate.reason());
    } else {
      record(id, "SUFFICIENCY", Map.of("round", Integer.toString(round), "result", "stop"));
      timeline.add("Stopped. Confidence is below the bar, so no action was proposed.");
    }

    return new WarRoomView(
        id,
        bundle.focusService(),
        environment,
        phase,
        round,
        followUp,
        roundFindings,
        hypothesis,
        proposal,
        planHash,
        gateEffect,
        gateReason,
        approvalId,
        MODEL,
        MODEL_VERSION,
        List.copyOf(timeline),
        null);
  }

  private static List<Finding> prefix(List<Finding> findings, int round) {
    List<Finding> out = new ArrayList<>();
    for (Finding finding : findings) {
      out.add(
          new Finding(
              "r" + round + "-" + finding.id(),
              finding.agent(),
              finding.service(),
              finding.timestamp(),
              finding.name(),
              finding.value(),
              finding.baseline(),
              finding.instructionStripped(),
              finding.secretRedacted()));
    }
    return out;
  }

  private List<Finding> fanOut(EvidenceBundle bundle) {
    try {
      List<Future<List<Finding>>> futures =
          pool.invokeAll(
              List.of(
                  () -> Analysts.metrics(bundle),
                  () -> Analysts.logs(bundle),
                  () -> Analysts.topology(bundle),
                  () -> Analysts.deploys(bundle)));
      List<Finding> all = new ArrayList<>();
      for (Future<List<Finding>> future : futures) {
        all.addAll(future.get());
      }
      return all;
    } catch (Exception e) {
      throw new IllegalStateException("Specialist fan-out failed", e);
    }
  }

  private void record(String incidentId, String eventType, Map<String, String> attributes) {
    Map<String, String> attrs = new LinkedHashMap<>(attributes);
    attrs.put("model", MODEL);
    attrs.put("modelVersion", MODEL_VERSION);
    port.record("war-room-manager", incidentId, eventType, attrs);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
