package com.pulsegraph.guardian.web;

import com.pulsegraph.guardian.GovernanceService;
import com.pulsegraph.guardian.model.EvidencePack;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One-click regulatory evidence pack export for an incident. */
@RestController
@RequestMapping("/v1/evidence-packs")
public class EvidencePackController {

  private final GovernanceService governance;

  public EvidencePackController(GovernanceService governance) {
    this.governance = governance;
  }

  @GetMapping("/{incidentId}")
  public EvidencePack export(@PathVariable String incidentId) {
    return governance.buildEvidencePack(incidentId);
  }
}
