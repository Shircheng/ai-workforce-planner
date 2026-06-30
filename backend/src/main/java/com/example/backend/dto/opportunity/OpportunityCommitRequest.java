package com.example.backend.dto.opportunity;

import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpportunityCommitRequest {

    @Valid
    @NotNull(message = "Parsed opportunity is required before generating options")
    private Opportunity opportunity;

    @Valid
    @NotEmpty(message = "At least one parsed opportunity role is required before generating options")
    private List<OpportunityRole> roles;
}