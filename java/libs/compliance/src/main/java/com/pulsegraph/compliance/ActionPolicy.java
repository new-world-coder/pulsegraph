package com.pulsegraph.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Fail-closed action floor loaded from {@code action-policy.json}. A model confidence score is not
 * an input. Unknown operations and out-of-range parameters are refused.
 */
public final class ActionPolicy {

  private final List<Pattern> denyPatterns;
  private final Pattern dataStoreTarget;
  private final Map<String, Map<String, int[]>> allow;

  private ActionPolicy(
      List<Pattern> denyPatterns, Pattern dataStoreTarget, Map<String, Map<String, int[]>> allow) {
    this.denyPatterns = List.copyOf(denyPatterns);
    this.dataStoreTarget = dataStoreTarget;
    this.allow = Map.copyOf(allow);
  }

  public static ActionPolicy loadDefault() {
    InputStream in = ActionPolicy.class.getResourceAsStream("/action-policy.json");
    if (in == null) {
      throw new IllegalStateException("action-policy.json is missing; refusing to start without a floor");
    }
    try (in) {
      return fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("action-policy.json could not be read", e);
    }
  }

  public static ActionPolicy fromJson(String json) {
    try {
      JsonNode root = new ObjectMapper().readTree(json);
      List<Pattern> deny = new ArrayList<>();
      for (JsonNode n : root.path("denyPatterns")) {
        deny.add(Pattern.compile(n.asText()));
      }
      Pattern stores = Pattern.compile(root.path("dataStoreTarget").asText("a^"));
      Map<String, Map<String, int[]>> allow = new LinkedHashMap<>();
      for (JsonNode rule : root.path("allow")) {
        String operation = rule.path("operation").asText("");
        Map<String, int[]> bounds = new LinkedHashMap<>();
        JsonNode params = rule.path("parameters");
        params.fieldNames().forEachRemaining(name -> {
          JsonNode bound = params.get(name);
          bounds.put(name, new int[] {bound.path("min").asInt(), bound.path("max").asInt()});
        });
        if (!operation.isBlank()) {
          allow.put(operation, Map.copyOf(bounds));
        }
      }
      if (allow.isEmpty()) {
        throw new IllegalStateException("allow list is empty; refusing an open policy");
      }
      return new ActionPolicy(deny, stores, allow);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("action policy failed to parse", e);
    }
  }

  /**
   * @return a refusal reason, or empty when the request is within the floor. Requests with no
   *     remediation plan skip the allow list so existing non-remediation authorisations still work.
   *     They are still subject to the deny patterns and the data-store target check.
   */
  public Optional<String> denial(ActionRequest req) {
    String haystack =
        String.join(
            "\n",
            nz(req.action()),
            nz(req.tool()),
            nz(req.justification()),
            nz(req.targetService()),
            req.plan() == null ? "" : nz(req.plan().operation()),
            req.plan() == null ? "" : nz(req.plan().expectedEffect()),
            req.plan() == null ? "" : nz(req.plan().rollback()));
    for (Pattern p : denyPatterns) {
      if (p.matcher(haystack).find()) {
        return Optional.of(
            "Denied by the action deny list. Destructive, data-store, or IAM patterns cannot run.");
      }
    }
    if (req.targetService() != null && dataStoreTarget.matcher(req.targetService()).find()) {
      return Optional.of(
          "Denied: target '" + req.targetService() + "' is a data store and is outside the allow list.");
    }
    if (!req.serviceBoundary().isEmpty()
        && (req.targetService() == null || !req.serviceBoundary().contains(req.targetService()))) {
      return Optional.of(
          "Denied: target is outside the incident service boundary " + req.serviceBoundary() + ".");
    }
    if (req.plan() == null) {
      return Optional.empty();
    }
    RemediationPlan plan = req.plan();
    Map<String, int[]> bounds = allow.get(plan.operation());
    if (bounds == null) {
      return Optional.of(
          "Denied: operation '" + plan.operation() + "' is not on the scoped allow list.");
    }
    for (Map.Entry<String, String> param : plan.parameters().entrySet()) {
      int[] range = bounds.get(param.getKey());
      if (range == null) {
        return Optional.of(
            "Denied: parameter '" + param.getKey() + "' is not allowed on " + plan.operation() + ".");
      }
      int value;
      try {
        value = Integer.parseInt(param.getValue());
      } catch (NumberFormatException e) {
        return Optional.of("Denied: parameter '" + param.getKey() + "' must be an integer.");
      }
      if (value < range[0] || value > range[1]) {
        return Optional.of(
            "Denied: "
                + param.getKey()
                + "="
                + value
                + " is outside "
                + range[0]
                + ".."
                + range[1]
                + ".");
      }
    }
    for (String required : bounds.keySet()) {
      if (!plan.parameters().containsKey(required)) {
        return Optional.of(
            "Denied: " + plan.operation() + " requires parameter '" + required + "'.");
      }
    }
    return Optional.empty();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
