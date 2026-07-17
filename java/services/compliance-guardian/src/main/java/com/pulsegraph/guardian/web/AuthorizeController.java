package com.pulsegraph.guardian.web;

import com.pulsegraph.compliance.ActionRequest;
import com.pulsegraph.guardian.GovernanceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authorization API. An agent submits a proposed action and receives a policy decision
 * (ALLOW / REQUIRE_APPROVAL / DENY). When approval is required, a pending approval id is returned.
 * The guardian never executes the action itself.
 */
@RestController
@RequestMapping("/v1/authorize")
public class AuthorizeController {

  private final GovernanceService governance;

  public AuthorizeController(GovernanceService governance) {
    this.governance = governance;
  }

  @PostMapping
  public GovernanceService.AuthorizationResult authorize(@RequestBody ActionRequest req) {
    return governance.authorize(req);
  }
}
