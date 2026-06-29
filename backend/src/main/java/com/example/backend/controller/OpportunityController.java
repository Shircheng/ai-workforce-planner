package com.example.backend.controller;

import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityRole;
import com.example.backend.repository.OpportunityRepository;
import com.example.backend.repository.OpportunityRoleRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/opportunities")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class OpportunityController {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityRoleRepository opportunityRoleRepository;

    public OpportunityController(
            OpportunityRepository opportunityRepository,
            OpportunityRoleRepository opportunityRoleRepository
    ) {
        this.opportunityRepository = opportunityRepository;
        this.opportunityRoleRepository = opportunityRoleRepository;
    }

    @GetMapping("/{opportunityId}")
    public Opportunity getOpportunity(@PathVariable String opportunityId) {
        return opportunityRepository.findByOpportunityId(opportunityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Opportunity not found: " + opportunityId
                ));
    }

    @GetMapping("/{opportunityId}/roles")
    @ResponseStatus(HttpStatus.OK)
    public List<OpportunityRole> getOpportunityRoles(@PathVariable String opportunityId) {
        return opportunityRoleRepository.findByOpportunityId(opportunityId);
    }
}
