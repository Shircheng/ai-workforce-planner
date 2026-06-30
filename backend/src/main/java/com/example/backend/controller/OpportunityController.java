package com.example.backend.controller;

import com.example.backend.entity.Opportunity;
import com.example.backend.repository.OpportunityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.example.backend.dto.opportunity.OpportunityCommitRequest;
import com.example.backend.dto.opportunity.OpportunityParseResponse;
import com.example.backend.dto.opportunity.OpportunityRequest;
import com.example.backend.service.OpportunityParsingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityParsingService opportunityParsingService;

    public OpportunityController(
        OpportunityRepository opportunityRepository,
        OpportunityParsingService opportunityParsingService
    ) {
        this.opportunityRepository = opportunityRepository;
        this.opportunityParsingService = opportunityParsingService;
    }

    @GetMapping("/{opportunityId}")
    public Opportunity getOpportunity(@PathVariable String opportunityId) {
        return opportunityRepository.findByOpportunityId(opportunityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Opportunity not found: " + opportunityId
                ));
    }

    @PostMapping("/parse")
    public OpportunityParseResponse parse(@Valid @RequestBody OpportunityRequest request) {
        return opportunityParsingService.parseAndValidate(request);
    }

    @PostMapping("/generate-options")
    @ResponseStatus(HttpStatus.CREATED)
    public OpportunityParseResponse storeForRecommender(@Valid @RequestBody OpportunityCommitRequest request) {
        return opportunityParsingService.storeForRecommender(request);
    }


    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? "Unable to process the opportunity request."
                : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }
}