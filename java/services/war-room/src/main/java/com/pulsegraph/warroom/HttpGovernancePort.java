package com.pulsegraph.warroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsegraph.compliance.ActionRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Talks to a remote compliance-guardian. Any transport failure fails closed. */
public final class HttpGovernancePort implements GovernancePort {

  private final URI base;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private final ObjectMapper json = new ObjectMapper();

  public HttpGovernancePort(String baseUrl) {
    String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.base = URI.create(trimmed);
  }

  @Override
  public Gate authorize(ActionRequest request) {
    try {
      JsonNode body = post("/v1/authorize", request);
      String effect = body.path("decision").path("effect").asText("DENY");
      String reason = body.path("decision").path("reason").asText("Guardian refused the proposal.");
      String approvalId = textOrNull(body, "approvalId");
      String planHash =
          request.plan() == null
              ? request.planHash()
              : com.pulsegraph.compliance.PlanHasher.hash(request.plan());
      return new Gate(effect, reason, approvalId, planHash);
    } catch (Exception e) {
      return new Gate("DENY", "Guardian unreachable: " + e.getMessage(), null, request.planHash());
    }
  }

  @Override
  public void record(
      String agentId, String incidentId, String eventType, Map<String, String> attributes) {
    try {
      post(
          "/v1/audit/events",
          Map.of(
              "agentId", agentId == null ? "" : agentId,
              "incidentId", incidentId == null ? "" : incidentId,
              "eventType", eventType,
              "attributes", attributes == null ? Map.of() : attributes));
    } catch (Exception ignored) {
      // The investigation can still return. The gate itself fails closed in authorize and permit.
    }
  }

  @Override
  public boolean decide(String approvalId, String approver, boolean granted) {
    try {
      HttpResponse<String> response =
          send(
              "/v1/approvals/" + approvalId + "/decide",
              Map.of(
                  "approver", approver,
                  "role", "incident-commander",
                  "granted", granted,
                  "comment", "bound to the plan hash"));
      return response.statusCode() / 100 == 2;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Permit permit(String approvalId, String planHash, String idempotencyKey) {
    try {
      JsonNode body =
          post(
              "/v1/approvals/" + approvalId + "/permit",
              Map.of("planHash", planHash == null ? "" : planHash, "idempotencyKey", idempotencyKey));
      return new Permit(
          body.path("permitted").asBoolean(false),
          body.path("replay").asBoolean(false),
          body.path("reason").asText(""));
    } catch (Exception e) {
      return new Permit(false, false, "Guardian unreachable: " + e.getMessage());
    }
  }

  private JsonNode post(String path, Object body) throws Exception {
    HttpResponse<String> response = send(path, body);
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException("HTTP " + response.statusCode() + " from " + path);
    }
    if (response.body() == null || response.body().isBlank()) {
      return json.createObjectNode();
    }
    return json.readTree(response.body());
  }

  private HttpResponse<String> send(String path, Object body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(base.resolve(path))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String text = value.asText();
    return text.isBlank() ? null : text;
  }
}
