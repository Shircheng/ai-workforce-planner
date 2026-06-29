package com.example.backend.service;

import com.example.backend.dto.EwaRequestSubmitRequest;
import com.example.backend.entity.Employee;
import com.example.backend.entity.EwaRequest;
import com.example.backend.entity.EwaStatus;
import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityRole;
import com.example.backend.repository.EmployeeRepository;
import com.example.backend.repository.EwaRequestRepository;
import com.example.backend.repository.OpportunityRepository;
import com.example.backend.repository.OpportunityRoleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class EwaRequestService {

    private static final String REQUEST_TYPE = "Opportunity Proposal";
    private static final String APPROVAL_REQUIRED = "Yes";
    private static final String BOOKING_OWNER = "Regional Planner";
    private static final String DEFAULT_BLOCKING_REASON = "None";
    private static final String DEFAULT_NEXT_ACTION = "Submit for approval and reserve capability";

    private final EwaRequestRepository ewaRequestRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityRoleRepository opportunityRoleRepository;
    private final EmployeeRepository employeeRepository;

    public EwaRequestService(
            EwaRequestRepository ewaRequestRepository,
            OpportunityRepository opportunityRepository,
            OpportunityRoleRepository opportunityRoleRepository,
            EmployeeRepository employeeRepository
    ) {
        this.ewaRequestRepository = ewaRequestRepository;
        this.opportunityRepository = opportunityRepository;
        this.opportunityRoleRepository = opportunityRoleRepository;
        this.employeeRepository = employeeRepository;
    }

    public synchronized EwaRequest submitToEwa(EwaRequestSubmitRequest request) {
        Opportunity opportunity = opportunityRepository.findByOpportunityId(request.getOpportunityId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Opportunity not found: " + request.getOpportunityId()
                ));
        OpportunityRole opportunityRole = opportunityRoleRepository
                .findByOpportunityIdAndOpportunityRoleId(
                        request.getOpportunityId(),
                        request.getOpportunityRoleId()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Opportunity role not found: " + request.getOpportunityRoleId()
                ));
        Employee employee = employeeRepository.findByEmployeeId(request.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Employee not found: " + request.getEmployeeId()
                ));

        BigDecimal requestedFte = defaultDecimal(opportunityRole.getFteRequired());
        LocalDate proposedStartDate = opportunity.getExpectedStartDate();
        LocalDate proposedEndDate = calculateProposedEndDate(opportunity);
        BigDecimal availableFteAtStart = request.getAvailableFTEAtStart() != null
                ? defaultDecimal(request.getAvailableFTEAtStart())
                : calculateAvailableFteAtStart(employee, proposedStartDate);
        BigDecimal fteGap = request.getFteGap() != null
                ? defaultDecimal(request.getFteGap())
                : calculateFteGap(requestedFte, availableFteAtStart);

        EwaRequest ewaRequest = EwaRequest.builder()
                .ewaRequestId(generateEwaRequestId())
                .build();

        ewaRequest.setOpportunityId(request.getOpportunityId());
        ewaRequest.setOpportunityRoleId(request.getOpportunityRoleId());
        ewaRequest.setEmployeeId(request.getEmployeeId());
        ewaRequest.setEmployeeName(employee.getEmployeeName());
        ewaRequest.setRequestType(REQUEST_TYPE);
        ewaRequest.setEwaStatus(EwaStatus.PENDING_APPROVAL.getLabel());
        ewaRequest.setRequestedFTE(requestedFte);
        ewaRequest.setProposedStartDate(proposedStartDate);
        ewaRequest.setProposedEndDate(proposedEndDate);
        ewaRequest.setApprovalRequired(APPROVAL_REQUIRED);
        ewaRequest.setBookingOwner(BOOKING_OWNER);
        ewaRequest.setBlockingReason(DEFAULT_BLOCKING_REASON);
        ewaRequest.setNextAction(DEFAULT_NEXT_ACTION);
        ewaRequest.setLastUpdated(LocalDate.now());
        ewaRequest.setNotes(request.getNotes());
        ewaRequest.setAvailableFTEAtStart(availableFteAtStart);
        ewaRequest.setFteGap(fteGap);
        ewaRequest.setCanSplitRole(normalizeYesNo(opportunityRole.getCanCombineCandidates()));
        ewaRequest.setEarliestFullAvailabilityDate(employee.getExpectedReleaseDate());

        return ewaRequestRepository.save(ewaRequest);
    }

    private BigDecimal calculateAvailableFteAtStart(Employee employee, LocalDate proposedStartDate) {
        LocalDate expectedReleaseDate = employee.getExpectedReleaseDate();
        if (expectedReleaseDate != null
                && proposedStartDate != null
                && !expectedReleaseDate.isAfter(proposedStartDate)) {
            return BigDecimal.ONE;
        }
        return defaultDecimal(employee.getAvailableFTECurrent());
    }

    private BigDecimal calculateFteGap(BigDecimal requestedFte, BigDecimal availableFteAtStart) {
        BigDecimal gap = requestedFte.subtract(availableFteAtStart);
        if (gap.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return gap;
    }

    private LocalDate calculateProposedEndDate(Opportunity opportunity) {
        if (opportunity.getExpectedStartDate() == null || opportunity.getDurationWeeks() == null) {
            return null;
        }
        return opportunity.getExpectedStartDate().plusWeeks(opportunity.getDurationWeeks());
    }

    private String normalizeYesNo(String value) {
        if (value == null || value.isBlank()) {
            return "No";
        }
        return "Yes".equalsIgnoreCase(value) ? "Yes" : "No";
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateEwaRequestId() {
        int nextNumber = ewaRequestRepository.findByEwaRequestIdStartingWith("EWA-")
                .stream()
                .map(EwaRequest::getEwaRequestId)
                .mapToInt(this::parseEwaRequestNumber)
                .max()
                .orElse(0) + 1;
        return String.format("EWA-%05d", nextNumber);
    }

    private int parseEwaRequestNumber(String ewaRequestId) {
        if (ewaRequestId == null || !ewaRequestId.startsWith("EWA-")) {
            return 0;
        }
        try {
            return Integer.parseInt(ewaRequestId.substring(4));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
