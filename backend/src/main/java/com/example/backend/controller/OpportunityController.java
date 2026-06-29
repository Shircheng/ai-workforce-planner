package com.example.backend.controller;

import com.example.backend.entity.Opportunity;
import com.example.backend.repository.OpportunityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {

    private final OpportunityRepository opportunityRepository;

    public OpportunityController(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = opportunityRepository;
    }

    @GetMapping("/{opportunityId}")
    public Opportunity getOpportunity(@PathVariable String opportunityId) {
        return opportunityRepository.findByOpportunityId(opportunityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Opportunity not found: " + opportunityId
                ));
    }
}
