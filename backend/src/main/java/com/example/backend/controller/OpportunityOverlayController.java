package com.example.backend.controller;

import com.example.backend.entity.OpportunityOverlay;
import com.example.backend.repository.OpportunityOverlayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/opportunity-overlays")
public class OpportunityOverlayController {

    private final OpportunityOverlayRepository opportunityOverlayRepository;

    public OpportunityOverlayController(OpportunityOverlayRepository opportunityOverlayRepository) {
        this.opportunityOverlayRepository = opportunityOverlayRepository;
    }

    @GetMapping("/{overlayId}")
    public OpportunityOverlay getOpportunityOverlay(@PathVariable String overlayId) {
        return opportunityOverlayRepository.findByOverlayId(overlayId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Opportunity overlay not found: " + overlayId
                ));
    }
}
