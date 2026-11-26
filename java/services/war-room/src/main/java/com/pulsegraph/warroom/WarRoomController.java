package com.pulsegraph.warroom;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/war-rooms")
public class WarRoomController {

  private final WarRoomManager manager;

  public WarRoomController(WarRoomManager manager) {
    this.manager = manager;
  }

  @PostMapping
  public WarRoomView open(@RequestBody OpenRequest request) {
    Detection detection =
        new Detection(request.trigger(), request.service(), request.environment(), request.summary());
    return manager.open(detection, request.evidence());
  }

  @GetMapping("/{id}")
  public ResponseEntity<WarRoomView> get(@PathVariable("id") String id) {
    try {
      return ResponseEntity.ok(manager.get(id));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.notFound().build();
    }
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<WarRoomView> approve(
      @PathVariable("id") String id, @RequestBody ApproveRequest request) {
    try {
      return ResponseEntity.ok(manager.approve(id, request.approver()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.notFound().build();
    }
  }

  @PostMapping("/{id}/canary")
  public ResponseEntity<WarRoomView> canary(
      @PathVariable("id") String id, @RequestBody CanaryRequest request) {
    try {
      return ResponseEntity.ok(
          manager.runCanary(id, request.planHash(), request.idempotencyKey(), request.signal()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.notFound().build();
    }
  }

  public record OpenRequest(
      String trigger, String service, String environment, String summary, EvidenceBundle evidence) {}

  public record ApproveRequest(String approver) {}

  public record CanaryRequest(String planHash, String idempotencyKey, ExpectedSignal signal) {}
}
