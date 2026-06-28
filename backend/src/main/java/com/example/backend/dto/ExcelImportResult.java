package com.example.backend.dto;

import java.util.List;

public record ExcelImportResult(
        int employeeCount,
        int employeeSkillCount,
        int availabilityCount,
        int allocationCount,
        int benchCount,
        int profileCount,
        int projectHistoryCount,
        int skillCatalogCount,
        int opportunityCount,
        int opportunityRoleCount,
        List<String> skippedSheets
) {
}
