package com.example.backend.controller;

import com.example.backend.entity.OpportunityRole;
import com.example.backend.repository.OpportunityRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/opportunity-roles")
public class OpportunityRoleController {

    private final OpportunityRoleRepository opportunityRoleRepository;

    public OpportunityRoleController(OpportunityRoleRepository opportunityRoleRepository) {
        this.opportunityRoleRepository = opportunityRoleRepository;
    }

    @GetMapping("/{opportunityRoleId}")
    public OpportunityRole getOpportunityRole(@PathVariable String opportunityRoleId) {
        return opportunityRoleRepository.findByOpportunityRoleId(opportunityRoleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Opportunity role not found: " + opportunityRoleId
                ));
    }
}
