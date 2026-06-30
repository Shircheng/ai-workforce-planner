package com.example.backend.dto.opportunity;

import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityRole;

import java.util.List;

public record OpportunityParseResponse(
        Opportunity opportunity,
        List<OpportunityRole> roles,
        List<ValidationIssue> validationIssues,
        String parseSource
) {
}
