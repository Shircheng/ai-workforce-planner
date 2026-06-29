package com.example.backend.entity;

public enum EwaStatus {
    DRAFT("Draft"),
    PENDING_APPROVAL("Pending Approval"),
    APPROVED("Approved"),
    BLOCKED("Blocked");

    private final String label;

    EwaStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
