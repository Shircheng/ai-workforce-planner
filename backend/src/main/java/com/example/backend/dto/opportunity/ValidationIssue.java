package com.example.backend.dto.opportunity;

public record ValidationIssue(
        String field,
        String severity,
        String message
) {
}
