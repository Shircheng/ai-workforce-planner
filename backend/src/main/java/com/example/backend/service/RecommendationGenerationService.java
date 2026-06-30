package com.example.backend.service;

import com.example.backend.dto.recommendation.RecommendationGenerateResponse;
import com.example.backend.dto.recommendation.RecommendationExplanationUpdateRequest;
import com.example.backend.dto.recommendation.RecommendationOptionDto;
import com.example.backend.dto.recommendation.RecommendationOptionMemberDto;
import com.example.backend.entity.Allocation;
import com.example.backend.entity.Availability;
import com.example.backend.entity.Bench;
import com.example.backend.entity.Employee;
import com.example.backend.entity.EmployeeSkill;
import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityOverlay;
import com.example.backend.entity.OpportunityRole;
import com.example.backend.entity.Profile;
import com.example.backend.entity.ProjectHistory;
import com.example.backend.entity.RecommendationRun;
import com.example.backend.entity.RecommendationRunMember;
import com.example.backend.entity.RecommendationRunOption;
import com.example.backend.repository.AllocationRepository;
import com.example.backend.repository.AvailabilityRepository;
import com.example.backend.repository.BenchRepository;
import com.example.backend.repository.EmployeeRepository;
import com.example.backend.repository.EmployeeSkillRepository;
import com.example.backend.repository.OpportunityOverlayRepository;
import com.example.backend.repository.OpportunityRepository;
import com.example.backend.repository.OpportunityRoleRepository;
import com.example.backend.repository.ProfileRepository;
import com.example.backend.repository.ProjectHistoryRepository;
import com.example.backend.repository.RecommendationRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationGenerationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_OVERLAYS_PER_ROLE = 3;
    private static final String PLANNER_NOTES =
            "Capability and availability are scored separately. EWA Requests is the booking-status source of truth.";
    private static final String OPTION_SKILL_FIT = "BEST_SKILL_FIT";
    private static final String OPTION_FASTEST = "FASTEST_AVAILABLE_TEAM";
    private static final String OPTION_BALANCED = "BALANCED_LOW_RISK_TEAM";
    private static final BigDecimal DIVERSIFICATION_SCORE_TOLERANCE = BigDecimal.valueOf(15);
    private static final BigDecimal DIVERSIFICATION_MIN_AVAILABILITY_SCORE = BigDecimal.valueOf(70);
    private static final BigDecimal DIVERSIFICATION_MAX_RISK_INCREASE = BigDecimal.valueOf(20);

    private final OpportunityRepository opportunityRepository;
    private final OpportunityRoleRepository opportunityRoleRepository;
    private final OpportunityOverlayRepository opportunityOverlayRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ProfileRepository profileRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final AllocationRepository allocationRepository;
    private final BenchRepository benchRepository;
    private final RecommendationRunRepository recommendationRunRepository;

    public RecommendationGenerationService(
            OpportunityRepository opportunityRepository,
            OpportunityRoleRepository opportunityRoleRepository,
            OpportunityOverlayRepository opportunityOverlayRepository,
            EmployeeRepository employeeRepository,
            EmployeeSkillRepository employeeSkillRepository,
            AvailabilityRepository availabilityRepository,
            ProfileRepository profileRepository,
            ProjectHistoryRepository projectHistoryRepository,
            AllocationRepository allocationRepository,
            BenchRepository benchRepository,
            RecommendationRunRepository recommendationRunRepository
    ) {
        this.opportunityRepository = opportunityRepository;
        this.opportunityRoleRepository = opportunityRoleRepository;
        this.opportunityOverlayRepository = opportunityOverlayRepository;
        this.employeeRepository = employeeRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.availabilityRepository = availabilityRepository;
        this.profileRepository = profileRepository;
        this.projectHistoryRepository = projectHistoryRepository;
        this.allocationRepository = allocationRepository;
        this.benchRepository = benchRepository;
        this.recommendationRunRepository = recommendationRunRepository;
    }

    public RecommendationGenerateResponse generateRecommendations(String opportunityId) {
        Opportunity opportunity = opportunityRepository.findByOpportunityId(opportunityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Opportunity not found: " + opportunityId
                ));

        List<OpportunityRole> roles = opportunityRoleRepository.findByOpportunityId(opportunityId);
        if (roles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No opportunity roles found for opportunityId: " + opportunityId
            );
        }

        List<Employee> employees = employeeRepository.findAll();
        if (employees.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No employees found. Import dataset first.");
        }

        Map<String, Employee> employeesById = employees.stream()
                .filter(employee -> employee.getEmployeeId() != null)
                .collect(Collectors.toMap(Employee::getEmployeeId, employee -> employee, (left, right) -> left));

        Map<String, List<EmployeeSkill>> skillsByEmployee = employeeSkillRepository.findAll().stream()
                .filter(skill -> skill.getEmployeeId() != null)
                .collect(Collectors.groupingBy(EmployeeSkill::getEmployeeId));

        Map<String, List<Availability>> availabilityByEmployee = availabilityRepository.findAll().stream()
                .filter(availability -> availability.getEmployeeId() != null)
                .collect(Collectors.groupingBy(Availability::getEmployeeId));

        Map<String, Profile> profileByEmployee = profileRepository.findAll().stream()
                .filter(profile -> profile.getEmployeeId() != null)
                .collect(Collectors.toMap(Profile::getEmployeeId, profile -> profile, (left, right) -> left));

        Map<String, List<ProjectHistory>> projectHistoryByEmployee = projectHistoryRepository.findAll().stream()
                .filter(projectHistory -> projectHistory.getEmployeeId() != null)
                .collect(Collectors.groupingBy(ProjectHistory::getEmployeeId));

        Map<String, List<Allocation>> allocationsByEmployee = allocationRepository.findAll().stream()
                .filter(allocation -> allocation.getEmployeeId() != null)
                .collect(Collectors.groupingBy(Allocation::getEmployeeId));

        Map<String, List<Bench>> benchByEmployee = benchRepository.findAll().stream()
                .filter(bench -> bench.getEmployeeId() != null)
                .collect(Collectors.groupingBy(Bench::getEmployeeId));

        Map<String, List<CandidateResult>> rankedCandidatesByRole = new HashMap<>();
        Map<String, CandidateResult> candidateResultByAssignment = new HashMap<>();
        for (OpportunityRole role : roles) {
            if (role.getOpportunityRoleId() == null) {
                continue;
            }

            List<CandidateResult> roleCandidates = new ArrayList<>();
            for (Employee employee : employees) {
                if (employee.getEmployeeId() == null) {
                    continue;
                }

                CandidateResult result = scoreCandidate(
                        opportunity,
                        role,
                        employee,
                        skillsByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        availabilityByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        profileByEmployee.get(employee.getEmployeeId()),
                        projectHistoryByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        allocationsByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        benchByEmployee.getOrDefault(employee.getEmployeeId(), List.of())
                );

                roleCandidates.add(result);
                candidateResultByAssignment.put(
                        assignmentKey(role.getOpportunityRoleId(), employee.getEmployeeId()),
                        result
                );
            }

            rankedCandidatesByRole.put(role.getOpportunityRoleId(), sortCandidatesForOverlay(roleCandidates));
        }

        List<OpportunityRole> orderedRoles = orderRolesForOverlayAssignment(roles, rankedCandidatesByRole);
        List<OpportunityOverlay> generatedOverlays = new ArrayList<>();
        Set<String> selectedEmployeeIdsAcrossRoles = new HashSet<>();

        for (OpportunityRole role : orderedRoles) {
            List<CandidateResult> rankedCandidates =
                    rankedCandidatesByRole.getOrDefault(role.getOpportunityRoleId(), List.of());
            List<CandidateResult> selectedForRole = selectOverlayCandidatesForRole(
                    rankedCandidates,
                    selectedEmployeeIdsAcrossRoles
            );

            int rank = 1;
            for (CandidateResult candidate : selectedForRole) {
                String employeeId = candidate.employee().getEmployeeId();
                if (employeeId == null) {
                    continue;
                }

                OpportunityOverlay overlay = toOverlay(opportunity, role, candidate, rank++);
                generatedOverlays.add(overlay);
                selectedEmployeeIdsAcrossRoles.add(employeeId);
            }
        }

        opportunityOverlayRepository.deleteByOpportunityId(opportunityId);
        assignSequentialOverlayIds(generatedOverlays);
        opportunityOverlayRepository.saveAll(generatedOverlays);

        Map<String, OpportunityRole> roleById = roles.stream()
                .filter(role -> role.getOpportunityRoleId() != null)
                .collect(Collectors.toMap(OpportunityRole::getOpportunityRoleId, role -> role));

        List<RecommendationOptionDto> options = new ArrayList<>();
        RecommendationOptionDto bestSkillOption = buildOption(
                OPTION_SKILL_FIT,
                generatedOverlays,
                roleById,
                candidateResultByAssignment,
                skillsByEmployee,
                List.of()
        );
        options.add(bestSkillOption);

        RecommendationOptionDto fastestOption = buildOption(
                OPTION_FASTEST,
                generatedOverlays,
                roleById,
                candidateResultByAssignment,
                skillsByEmployee,
                options
        );
        options.add(fastestOption);

        RecommendationOptionDto balancedOption = buildOption(
                OPTION_BALANCED,
                generatedOverlays,
                roleById,
                candidateResultByAssignment,
                skillsByEmployee,
                options
        );
        options.add(balancedOption);

        String recommendationRunId = "RRUN-" + UUID.randomUUID();
        Instant generatedAt = Instant.now();

        RecommendationGenerateResponse response = RecommendationGenerateResponse.builder()
                .recommendationRunId(recommendationRunId)
                .opportunityId(opportunity.getOpportunityId())
                .opportunityName(opportunity.getOpportunityName())
                .generatedAt(generatedAt)
                .overlayCount(generatedOverlays.size())
                .explanationStatus("PENDING_AI_INTEGRATION")
                .options(options)
                .build();

        recommendationRunRepository.save(toRecommendationRun(response));

        return response;
    }

    public RecommendationRun getRecommendationRun(String recommendationRunId) {
        return recommendationRunRepository.findByRecommendationRunId(recommendationRunId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recommendation run not found: " + recommendationRunId
                ));
    }

    public RecommendationRun getLatestRecommendationRunForOpportunity(String opportunityId) {
        return recommendationRunRepository.findTopByOpportunityIdOrderByGeneratedAtDesc(opportunityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No recommendation runs found for opportunityId: " + opportunityId
                ));
    }

    public RecommendationRun updateRecommendationExplanation(
            String recommendationRunId,
            RecommendationExplanationUpdateRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }

        RecommendationRun run = getRecommendationRun(recommendationRunId);

        if (!isBlank(request.getExplanationStatus())) {
            run.setExplanationStatus(request.getExplanationStatus());
        }
        if (request.getAiExplanation() != null) {
            run.setAiExplanation(request.getAiExplanation());
        }
        if (request.getAiGeneratedAt() != null) {
            run.setAiGeneratedAt(request.getAiGeneratedAt());
        } else if (request.getAiExplanation() != null) {
            run.setAiGeneratedAt(Instant.now());
        }

        run.setUpdatedAt(Instant.now());
        return recommendationRunRepository.save(run);
    }

    private RecommendationRun toRecommendationRun(RecommendationGenerateResponse response) {
        return RecommendationRun.builder()
                .recommendationRunId(response.getRecommendationRunId())
                .opportunityId(response.getOpportunityId())
                .opportunityName(response.getOpportunityName())
                .generatedAt(response.getGeneratedAt())
                .overlayCount(response.getOverlayCount())
                .explanationStatus(response.getExplanationStatus())
                .updatedAt(response.getGeneratedAt())
                .options(response.getOptions() == null ? List.of() : response.getOptions().stream()
                        .map(this::toRecommendationRunOption)
                        .toList())
                .build();
    }

    private RecommendationRunOption toRecommendationRunOption(RecommendationOptionDto option) {
        return RecommendationRunOption.builder()
                .optionType(option.getOptionType())
                .confidenceScore(option.getConfidenceScore())
                .riskScore(option.getRiskScore())
                .riskLevel(option.getRiskLevel())
                .readinessDays(option.getReadinessDays())
                .selectedMemberCount(option.getSelectedMemberCount())
                .locationFitScore(option.getLocationFitScore())
                .locationFit(uniqueList(option.getLocationFit()))
                .skillCoverageScore(option.getSkillCoverageScore())
                .matchedRequiredSkills(uniqueList(option.getMatchedRequiredSkills()))
                .missingRequiredSkills(uniqueList(option.getMissingRequiredSkills()))
                .matchedDesiredSkills(uniqueList(option.getMatchedDesiredSkills()))
                .missingDesiredSkills(uniqueList(option.getMissingDesiredSkills()))
                .risks(uniqueList(option.getRisks()))
                .members(option.getMembers() == null ? List.of() : option.getMembers().stream()
                        .map(this::toRecommendationRunMember)
                        .toList())
                .build();
    }

    private RecommendationRunMember toRecommendationRunMember(RecommendationOptionMemberDto member) {
        return RecommendationRunMember.builder()
                .opportunityRoleId(member.getOpportunityRoleId())
                .roleName(member.getRoleName())
                .employeeId(member.getEmployeeId())
                .employeeName(member.getEmployeeName())
                .rank(member.getRank())
                .fitStatus(member.getFitStatus())
                .matchScore(member.getMatchScore())
                .capabilityFitScore(member.getCapabilityFitScore())
                .availabilityFitScore(member.getAvailabilityFitScore())
                .overallStaffingScore(member.getOverallStaffingScore())
                .skillCoverageScore(member.getSkillCoverageScore())
                .matchedRequiredSkills(uniqueList(member.getMatchedRequiredSkills()))
                .missingRequiredSkills(uniqueList(member.getMissingRequiredSkills()))
                .matchedDesiredSkills(uniqueList(member.getMatchedDesiredSkills()))
                .missingDesiredSkills(uniqueList(member.getMissingDesiredSkills()))
                .availableFteAtStart(member.getAvailableFteAtStart())
                .fteGap(member.getFteGap())
                .earliestFullAvailabilityDate(member.getEarliestFullAvailabilityDate())
                .rationale(member.getRationale())
                .constraint(member.getConstraint())
                .build();
    }

    private List<OpportunityRole> orderRolesForOverlayAssignment(
            List<OpportunityRole> roles,
            Map<String, List<CandidateResult>> rankedCandidatesByRole
    ) {
        return roles.stream()
                .filter(role -> role.getOpportunityRoleId() != null)
                .sorted(Comparator
                        .comparingInt((OpportunityRole role) ->
                                viableCandidatesCount(rankedCandidatesByRole.getOrDefault(role.getOpportunityRoleId(), List.of())))
                        .thenComparingInt(role -> priorityOrder(role.getPriority()))
                        .thenComparing(role -> role.getOpportunityRoleId(), Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private int viableCandidatesCount(List<CandidateResult> candidates) {
        return (int) candidates.stream()
                .filter(this::hasRequiredSkillCoverage)
                .filter(candidate -> !hasFteGapAtStart(candidate))
                .count();
    }

    private int priorityOrder(String priority) {
        String normalized = normalize(priority);
        if ("high".equals(normalized)) {
            return 0;
        }
        if ("medium".equals(normalized)) {
            return 1;
        }
        return 2;
    }

    private List<CandidateResult> sortCandidatesForOverlay(List<CandidateResult> candidates) {
        return candidates.stream()
                .sorted(Comparator
                        .comparing(this::requiredCoverageForSort, Comparator.reverseOrder())
                        .thenComparing(CandidateResult::requiredSkillsMatched, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(this::hasFteGapAtStart)
                        .thenComparing(CandidateResult::availabilityFitScore, Comparator.reverseOrder())
                        .thenComparing(CandidateResult::overallStaffingScore, Comparator.reverseOrder())
                        .thenComparing(CandidateResult::capabilityFitScore, Comparator.reverseOrder()))
                .toList();
    }

    private BigDecimal requiredCoverageForSort(CandidateResult candidate) {
        int total = candidate.requiredSkillsTotal() == null ? 0 : candidate.requiredSkillsTotal();
        int matched = candidate.requiredSkillsMatched() == null ? 0 : candidate.requiredSkillsMatched();
        return ratio(matched, total);
    }

    private boolean hasRequiredSkillCoverage(CandidateResult candidate) {
        int total = candidate.requiredSkillsTotal() == null ? 0 : candidate.requiredSkillsTotal();
        int matched = candidate.requiredSkillsMatched() == null ? 0 : candidate.requiredSkillsMatched();
        return total == 0 || matched > 0;
    }

    private boolean hasFullRequiredCoverage(CandidateResult candidate) {
        int total = candidate.requiredSkillsTotal() == null ? 0 : candidate.requiredSkillsTotal();
        int matched = candidate.requiredSkillsMatched() == null ? 0 : candidate.requiredSkillsMatched();
        return total == 0 || matched == total;
    }

    private boolean hasFteGapAtStart(CandidateResult candidate) {
        return defaultBigDecimal(candidate.fteGap(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0;
    }

    private List<CandidateResult> selectOverlayCandidatesForRole(
            List<CandidateResult> rankedCandidates,
            Set<String> selectedEmployeeIdsAcrossRoles
    ) {
        List<CandidateResult> selected = new ArrayList<>();

        fillCandidatesForPhase(selected, rankedCandidates, selectedEmployeeIdsAcrossRoles, true, true, true);
        fillCandidatesForPhase(selected, rankedCandidates, selectedEmployeeIdsAcrossRoles, false, true, true);
        fillCandidatesForPhase(selected, rankedCandidates, selectedEmployeeIdsAcrossRoles, false, false, true);
        fillCandidatesForPhase(selected, rankedCandidates, selectedEmployeeIdsAcrossRoles, false, false, false);

        return selected;
    }

    private void fillCandidatesForPhase(
            List<CandidateResult> selected,
            List<CandidateResult> rankedCandidates,
            Set<String> selectedEmployeeIdsAcrossRoles,
            boolean requireFullCoverage,
            boolean requireNoFteGap,
            boolean requireAnyRequiredCoverage
    ) {
        if (selected.size() >= MAX_OVERLAYS_PER_ROLE) {
            return;
        }

        for (CandidateResult candidate : rankedCandidates) {
            if (selected.size() >= MAX_OVERLAYS_PER_ROLE) {
                break;
            }

            String employeeId = candidate.employee().getEmployeeId();
            if (employeeId == null || selectedEmployeeIdsAcrossRoles.contains(employeeId)) {
                continue;
            }
            if (selected.stream().anyMatch(current -> employeeId.equals(current.employee().getEmployeeId()))) {
                continue;
            }
            if (requireFullCoverage && !hasFullRequiredCoverage(candidate)) {
                continue;
            }
            if (requireNoFteGap && hasFteGapAtStart(candidate)) {
                continue;
            }
            if (requireAnyRequiredCoverage && !hasRequiredSkillCoverage(candidate)) {
                continue;
            }

            selected.add(candidate);
        }
    }

    private RecommendationOptionDto buildOption(
            String optionType,
            List<OpportunityOverlay> overlays,
            Map<String, OpportunityRole> roleById,
            Map<String, CandidateResult> candidateResultByAssignment,
            Map<String, List<EmployeeSkill>> skillsByEmployee,
            List<RecommendationOptionDto> previousOptions
    ) {
        Map<String, List<OpportunityOverlay>> overlaysByRole = overlays.stream()
                .filter(overlay -> overlay.getOpportunityRoleId() != null)
                .collect(Collectors.groupingBy(OpportunityOverlay::getOpportunityRoleId));

        List<OpportunityOverlay> selectedMembers = new ArrayList<>();
        Set<String> selectedEmployeeIds = new HashSet<>();
        List<String> orderedRoleIds = overlaysByRole.keySet().stream().sorted().toList();
        for (String roleId : orderedRoleIds) {
            OpportunityRole role = roleById.get(roleId);
            if (role == null) {
                continue;
            }

            List<OpportunityOverlay> sorted = sortForOption(overlaysByRole.get(roleId), optionType);
            List<OpportunityOverlay> uniqueCandidates = sorted.stream()
                    .filter(overlay -> !selectedEmployeeIds.contains(overlay.getEmployeeId()))
                    .toList();

            List<OpportunityOverlay> selectedForRole = selectTeamMembersForRole(uniqueCandidates, role);
            selectedMembers.addAll(selectedForRole);
            selectedEmployeeIds.addAll(selectedForRole.stream()
                    .map(OpportunityOverlay::getEmployeeId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        selectedMembers = diversifyIfDuplicate(
                optionType,
                selectedMembers,
                overlaysByRole,
                roleById,
                previousOptions
        );

        BigDecimal confidenceScore = average(selectedMembers.stream()
                .map(overlay -> optionScore(overlay, optionType))
                .toList());

        int readinessDays = selectedMembers.stream()
                .map(overlay -> readinessDelay(overlay, roleById.get(overlay.getOpportunityRoleId())))
                .max(Integer::compareTo)
                .orElse(0);

        List<String> risks = buildRisks(selectedMembers);
        BigDecimal riskScore = calculateRiskScore(selectedMembers, readinessDays, confidenceScore);
        String riskLevel = riskLevel(riskScore);

        List<RecommendationOptionMemberDto> members = selectedMembers.stream()
                .map(overlay -> toRecommendationOptionMember(overlay, roleById, candidateResultByAssignment))
                .toList();
        TeamSkillEvidence teamSkillEvidence = buildTeamSkillEvidence(selectedMembers, roleById, skillsByEmployee);
        BigDecimal locationFitScore = average(selectedMembers.stream()
                .map(overlay -> candidateFor(overlay, candidateResultByAssignment))
                .filter(Objects::nonNull)
                .map(CandidateResult::locationFitScore)
                .toList());
        List<String> locationFit = buildOptionLocationFit(selectedMembers, candidateResultByAssignment);

        return RecommendationOptionDto.builder()
                .optionType(optionType)
                .confidenceScore(confidenceScore)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .readinessDays(readinessDays)
                .selectedMemberCount(members.size())
                .locationFitScore(locationFitScore)
                .locationFit(locationFit)
                .skillCoverageScore(teamSkillEvidence.skillCoverageScore())
                .matchedRequiredSkills(teamSkillEvidence.matchedRequiredSkills())
                .missingRequiredSkills(teamSkillEvidence.missingRequiredSkills())
                .matchedDesiredSkills(teamSkillEvidence.matchedDesiredSkills())
                .missingDesiredSkills(teamSkillEvidence.missingDesiredSkills())
                .risks(uniqueList(risks))
                .members(members)
                .build();
    }

    private RecommendationOptionMemberDto toRecommendationOptionMember(
            OpportunityOverlay overlay,
            Map<String, OpportunityRole> roleById,
            Map<String, CandidateResult> candidateResultByAssignment
    ) {
        CandidateResult candidate = candidateResultByAssignment.get(
                assignmentKey(overlay.getOpportunityRoleId(), overlay.getEmployeeId())
        );

        return RecommendationOptionMemberDto.builder()
                .opportunityRoleId(overlay.getOpportunityRoleId())
                .roleName(roleName(roleById.get(overlay.getOpportunityRoleId())))
                .employeeId(overlay.getEmployeeId())
                .employeeName(overlay.getEmployeeName())
                .rank(overlay.getRank())
                .fitStatus(overlay.getFitStatus())
                .matchScore(overlay.getMatchScore())
                .capabilityFitScore(overlay.getCapabilityFitScore())
                .availabilityFitScore(overlay.getAvailabilityFitScore())
                .overallStaffingScore(overlay.getOverallStaffingScore())
                .skillCoverageScore(candidate == null ? skillCoverageScoreFromCounts(overlay) : candidate.skillCoverageScore())
                .matchedRequiredSkills(candidate == null ? List.of() : candidate.matchedRequiredSkills())
                .missingRequiredSkills(candidate == null ? List.of() : candidate.missingRequiredSkills())
                .matchedDesiredSkills(candidate == null ? List.of() : candidate.matchedDesiredSkills())
                .missingDesiredSkills(candidate == null ? List.of() : candidate.missingDesiredSkills())
                .availableFteAtStart(overlay.getAvailableFTEAtStart())
                .fteGap(overlay.getFteGap())
                .earliestFullAvailabilityDate(overlay.getEarliestFullAvailabilityDate())
                .rationale(overlay.getRationale())
                .constraint(overlay.getConstraint())
                .build();
    }

    private CandidateResult candidateFor(
            OpportunityOverlay overlay,
            Map<String, CandidateResult> candidateResultByAssignment
    ) {
        return candidateResultByAssignment.get(
                assignmentKey(overlay.getOpportunityRoleId(), overlay.getEmployeeId())
        );
    }

    private List<String> buildOptionLocationFit(
            List<OpportunityOverlay> selectedMembers,
            Map<String, CandidateResult> candidateResultByAssignment
    ) {
        return selectedMembers.stream()
                .map(overlay -> candidateFor(overlay, candidateResultByAssignment))
                .filter(Objects::nonNull)
                .map(CandidateResult::employee)
                .map(this::locationLabel)
                .filter(location -> !location.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private String locationLabel(Employee employee) {
        if (employee == null || isBlank(employee.getCountry())) {
            return "";
        }

        return employee.getCountry().trim();
    }

    private TeamSkillEvidence buildTeamSkillEvidence(
            List<OpportunityOverlay> selectedMembers,
            Map<String, OpportunityRole> roleById,
            Map<String, List<EmployeeSkill>> skillsByEmployee
    ) {
        Set<String> teamSkills = selectedMembers.stream()
                .map(OpportunityOverlay::getEmployeeId)
                .filter(Objects::nonNull)
                .flatMap(employeeId -> skillsByEmployee.getOrDefault(employeeId, List.of()).stream())
                .map(EmployeeSkill::getSkillName)
                .map(this::normalize)
                .filter(skill -> !skill.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> requiredSkills = selectedMembers.stream()
                .map(member -> roleById.get(member.getOpportunityRoleId()))
                .filter(Objects::nonNull)
                .flatMap(role -> safeList(role.getRequiredSkills()).stream())
                .toList();
        List<String> desiredSkills = selectedMembers.stream()
                .map(member -> roleById.get(member.getOpportunityRoleId()))
                .filter(Objects::nonNull)
                .flatMap(role -> safeList(role.getDesiredSkills()).stream())
                .toList();

        SkillMatchEvidence requiredEvidence = skillMatchEvidence(requiredSkills, teamSkills);
        SkillMatchEvidence desiredEvidence = skillMatchEvidence(desiredSkills, teamSkills);

        return new TeamSkillEvidence(
                skillCoverageScore(requiredEvidence, desiredEvidence),
                requiredEvidence.matchedSkills(),
                requiredEvidence.missingSkills(),
                desiredEvidence.matchedSkills(),
                desiredEvidence.missingSkills()
        );
    }

    private BigDecimal skillCoverageScoreFromCounts(OpportunityOverlay overlay) {
        SkillMatchEvidence requiredEvidence = new SkillMatchEvidence(
                List.of(),
                List.of(),
                ratio(
                        overlay.getRequiredSkillsMatched() == null ? 0 : overlay.getRequiredSkillsMatched(),
                        overlay.getRequiredSkillsTotal() == null ? 0 : overlay.getRequiredSkillsTotal()
                ),
                overlay.getRequiredSkillsTotal() == null ? 0 : overlay.getRequiredSkillsTotal()
        );
        SkillMatchEvidence desiredEvidence = new SkillMatchEvidence(
                List.of(),
                List.of(),
                ratio(
                        overlay.getDesiredSkillsMatched() == null ? 0 : overlay.getDesiredSkillsMatched(),
                        overlay.getDesiredSkillsTotal() == null ? 0 : overlay.getDesiredSkillsTotal()
                ),
                overlay.getDesiredSkillsTotal() == null ? 0 : overlay.getDesiredSkillsTotal()
        );
        return skillCoverageScore(requiredEvidence, desiredEvidence);
    }

    private SkillMatchEvidence skillMatchEvidence(Collection<String> targetSkills, Set<String> candidateSkills) {
        Map<String, String> canonicalTargetSkills = canonicalSkillNames(targetSkills);
        if (canonicalTargetSkills.isEmpty()) {
            return new SkillMatchEvidence(List.of(), List.of(), HUNDRED, 0);
        }

        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();
        for (Map.Entry<String, String> targetSkill : canonicalTargetSkills.entrySet()) {
            if (candidateSkills.contains(targetSkill.getKey())) {
                matchedSkills.add(targetSkill.getValue());
            } else {
                missingSkills.add(targetSkill.getValue());
            }
        }

        return new SkillMatchEvidence(
                List.copyOf(matchedSkills),
                List.copyOf(missingSkills),
                ratio(matchedSkills.size(), canonicalTargetSkills.size()),
                canonicalTargetSkills.size()
        );
    }

    private BigDecimal skillCoverageScore(SkillMatchEvidence requiredEvidence, SkillMatchEvidence desiredEvidence) {
        BigDecimal weightedScore = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;

        if (requiredEvidence.totalSkills() > 0) {
            BigDecimal weight = BigDecimal.valueOf(0.70);
            weightedScore = weightedScore.add(requiredEvidence.coverageScore().multiply(weight));
            weightTotal = weightTotal.add(weight);
        }
        if (desiredEvidence.totalSkills() > 0) {
            BigDecimal weight = BigDecimal.valueOf(0.30);
            weightedScore = weightedScore.add(desiredEvidence.coverageScore().multiply(weight));
            weightTotal = weightTotal.add(weight);
        }
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return HUNDRED;
        }

        return weightedScore.divide(weightTotal, 2, RoundingMode.HALF_UP);
    }

    private Map<String, String> canonicalSkillNames(Collection<String> skills) {
        if (skills == null) {
            return Map.of();
        }

        Map<String, String> canonical = new java.util.LinkedHashMap<>();
        for (String skill : skills) {
            String normalized = normalize(skill);
            if (!normalized.isBlank()) {
                canonical.putIfAbsent(normalized, skill == null ? "" : skill.trim());
            }
        }
        return canonical;
    }

    private List<OpportunityOverlay> selectTeamMembersForRole(List<OpportunityOverlay> sorted, OpportunityRole role) {
        if (sorted.isEmpty()) {
            return List.of();
        }

        BigDecimal requiredFte = defaultBigDecimal(role.getFteRequired(), BigDecimal.ONE);
        BigDecimal minimumIndividualFte = defaultBigDecimal(role.getMinimumIndividualFTE(), BigDecimal.ZERO);
        boolean canCombine = parseBoolean(role.getCanCombineCandidates());

        if (!canCombine) {
            return List.of(sorted.getFirst());
        }

        List<OpportunityOverlay> selected = new ArrayList<>();
        BigDecimal coveredFte = BigDecimal.ZERO;

        for (OpportunityOverlay candidate : sorted) {
            BigDecimal candidateFte = defaultBigDecimal(candidate.getAvailableFTEAtStart(), BigDecimal.ZERO);
            if (candidateFte.compareTo(minimumIndividualFte) < 0) {
                continue;
            }

            selected.add(candidate);
            coveredFte = coveredFte.add(candidateFte);

            if (coveredFte.compareTo(requiredFte) >= 0) {
                break;
            }
        }

        if (selected.isEmpty()) {
            selected.add(sorted.getFirst());
        }

        return selected;
    }

    private List<OpportunityOverlay> diversifyIfDuplicate(
            String optionType,
            List<OpportunityOverlay> selectedMembers,
            Map<String, List<OpportunityOverlay>> overlaysByRole,
            Map<String, OpportunityRole> roleById,
            List<RecommendationOptionDto> previousOptions
    ) {
        if (previousOptions == null || previousOptions.isEmpty() || !duplicatesPreviousOption(selectedMembers, previousOptions)) {
            return selectedMembers;
        }

        List<String> orderedRoleIds = selectedMembers.stream()
                .map(OpportunityOverlay::getOpportunityRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        for (String roleId : orderedRoleIds) {
            OpportunityRole role = roleById.get(roleId);
            List<OpportunityOverlay> currentForRole = selectedMembers.stream()
                    .filter(member -> roleId.equals(member.getOpportunityRoleId()))
                    .toList();
            if (role == null || currentForRole.size() != 1) {
                continue;
            }

            OpportunityOverlay current = currentForRole.getFirst();
            Set<String> employeeIdsInOtherRoles = selectedMembers.stream()
                    .filter(member -> !roleId.equals(member.getOpportunityRoleId()))
                    .map(OpportunityOverlay::getEmployeeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<OpportunityOverlay> alternatives = sortForOption(
                    overlaysByRole.getOrDefault(roleId, List.of()),
                    optionType
            );

            for (OpportunityOverlay alternative : alternatives) {
                String employeeId = alternative.getEmployeeId();
                if (employeeId == null
                        || employeeId.equals(current.getEmployeeId())
                        || employeeIdsInOtherRoles.contains(employeeId)) {
                    continue;
                }
                if (!isAcceptableDiversificationAlternative(current, alternative, optionType)) {
                    continue;
                }

                List<OpportunityOverlay> proposedMembers = selectedMembers.stream()
                        .map(member -> roleId.equals(member.getOpportunityRoleId()) ? alternative : member)
                        .toList();

                if (!duplicatesPreviousOption(proposedMembers, previousOptions)) {
                    return proposedMembers;
                }
            }
        }

        return selectedMembers;
    }

    private boolean isAcceptableDiversificationAlternative(
            OpportunityOverlay current,
            OpportunityOverlay alternative,
            String optionType
    ) {
        BigDecimal alternativeAvailability = defaultBigDecimal(alternative.getAvailabilityFitScore(), BigDecimal.ZERO);
        if (alternativeAvailability.compareTo(DIVERSIFICATION_MIN_AVAILABILITY_SCORE) < 0) {
            return false;
        }

        BigDecimal currentCapability = defaultBigDecimal(current.getCapabilityFitScore(), BigDecimal.ZERO);
        BigDecimal alternativeCapability = defaultBigDecimal(alternative.getCapabilityFitScore(), BigDecimal.ZERO);
        if (alternativeCapability.add(DIVERSIFICATION_SCORE_TOLERANCE).compareTo(currentCapability) < 0) {
            return false;
        }

        BigDecimal currentOptionScore = optionScore(current, optionType);
        BigDecimal alternativeOptionScore = optionScore(alternative, optionType);
        if (alternativeOptionScore.add(DIVERSIFICATION_SCORE_TOLERANCE).compareTo(currentOptionScore) < 0) {
            return false;
        }

        BigDecimal currentRisk = memberRiskScore(current);
        BigDecimal alternativeRisk = memberRiskScore(alternative);
        return alternativeRisk.subtract(currentRisk).compareTo(DIVERSIFICATION_MAX_RISK_INCREASE) <= 0;
    }

    private boolean duplicatesPreviousOption(
            List<OpportunityOverlay> selectedMembers,
            List<RecommendationOptionDto> previousOptions
    ) {
        String selectedSignature = teamSignature(selectedMembers);
        return previousOptions.stream()
                .map(this::teamSignature)
                .anyMatch(selectedSignature::equals);
    }

    private String teamSignature(List<OpportunityOverlay> members) {
        return members.stream()
                .map(member -> safe(member.getOpportunityRoleId()) + ":" + safe(member.getEmployeeId()))
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String teamSignature(RecommendationOptionDto option) {
        if (option.getMembers() == null) {
            return "";
        }
        return option.getMembers().stream()
                .map(member -> safe(member.getOpportunityRoleId()) + ":" + safe(member.getEmployeeId()))
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private List<OpportunityOverlay> sortForOption(List<OpportunityOverlay> overlays, String optionType) {
        Comparator<OpportunityOverlay> comparator;
        if (OPTION_SKILL_FIT.equals(optionType)) {
            comparator = Comparator
                    .comparing(RecommendationGenerationService::scoreValueForSortCapability).reversed()
                    .thenComparing(RecommendationGenerationService::scoreValueForSortOverall, Comparator.reverseOrder())
                    .thenComparing(RecommendationGenerationService::scoreValueForSortAvailability, Comparator.reverseOrder());
        } else if (OPTION_FASTEST.equals(optionType)) {
            comparator = Comparator
                    .comparing(RecommendationGenerationService::scoreValueForSortAvailability).reversed()
                    .thenComparing(RecommendationGenerationService::earliestAvailabilityDateForSort)
                    .thenComparing(RecommendationGenerationService::scoreValueForSortOverall, Comparator.reverseOrder());
        } else {
            comparator = Comparator
                    .comparing(this::balancedScoreForSort).reversed()
                    .thenComparing(RecommendationGenerationService::scoreValueForSortOverall, Comparator.reverseOrder())
                    .thenComparing(RecommendationGenerationService::scoreValueForSortCapability, Comparator.reverseOrder())
                    .thenComparing(RecommendationGenerationService::scoreValueForSortAvailability, Comparator.reverseOrder());
        }

        return overlays.stream().sorted(comparator).toList();
    }

    private CandidateResult scoreCandidate(
            Opportunity opportunity,
            OpportunityRole role,
            Employee employee,
            List<EmployeeSkill> skills,
            List<Availability> availability,
            Profile profile,
            List<ProjectHistory> projectHistory,
            List<Allocation> allocations,
            List<Bench> benchRecords
    ) {
        Set<String> requiredSkills = normalizeValues(role.getRequiredSkills());
        Set<String> desiredSkills = normalizeValues(role.getDesiredSkills());
        Set<String> candidateSkills = normalizeValues(skills.stream().map(EmployeeSkill::getSkillName).toList());
        SkillMatchEvidence requiredEvidence = skillMatchEvidence(role.getRequiredSkills(), candidateSkills);
        SkillMatchEvidence desiredEvidence = skillMatchEvidence(role.getDesiredSkills(), candidateSkills);

        int requiredMatched = requiredEvidence.matchedSkills().size();
        int desiredMatched = desiredEvidence.matchedSkills().size();

        BigDecimal requiredCoverage = requiredEvidence.coverageScore();
        BigDecimal desiredCoverage = desiredEvidence.coverageScore();
        BigDecimal skillStrength = skillStrength(skills, requiredSkills, desiredSkills);
        BigDecimal skillCoverageScore = skillCoverageScore(requiredEvidence, desiredEvidence);

        BigDecimal skillsScore = weighted(
                requiredCoverage, BigDecimal.valueOf(0.70),
                desiredCoverage, BigDecimal.valueOf(0.20),
                skillStrength, BigDecimal.valueOf(0.10)
        );

        AvailabilitySummary availabilitySummary = availabilitySummary(opportunity, role, employee, availability);
        BigDecimal availabilityScore = availabilitySummary.score();

        BigDecimal domainScore = domainScore(opportunity, role, employee, profile, projectHistory);
        BigDecimal gradeScore = gradeScore(role.getGradePreference(), employee.getGrade());
        BigDecimal locationScore = locationScore(opportunity, role, employee);
        BigDecimal projectScore = projectRelevanceScore(opportunity, role, skills, projectHistory);

        BigDecimal requiredCoverageRatio = requiredSkills.isEmpty()
                ? BigDecimal.ONE
                : BigDecimal.valueOf(requiredMatched)
                        .divide(BigDecimal.valueOf(requiredSkills.size()), 4, RoundingMode.HALF_UP);
        BigDecimal desiredCoverageRatio = desiredSkills.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(desiredMatched)
                        .divide(BigDecimal.valueOf(desiredSkills.size()), 4, RoundingMode.HALF_UP);

        BigDecimal gradeContribution = gradeScore.multiply(BigDecimal.valueOf(0.10));
        BigDecimal domainContribution = domainScore.multiply(BigDecimal.valueOf(0.05));
        BigDecimal locationContribution = locationScore.multiply(BigDecimal.valueOf(0.05));
        BigDecimal strengthContribution = skillStrength.multiply(BigDecimal.valueOf(0.05));
        BigDecimal projectContribution = projectScore.multiply(BigDecimal.valueOf(0.05));

        BigDecimal capabilityRaw = BigDecimal.valueOf(30)
                .add(requiredCoverageRatio.multiply(BigDecimal.valueOf(35)))
                .add(desiredCoverageRatio.multiply(BigDecimal.valueOf(15)))
                .add(strengthContribution)
                .add(gradeContribution)
                .add(domainContribution)
                .add(locationContribution)
                .add(projectContribution);

        BigDecimal capabilityScore = capabilityRaw.max(BigDecimal.ZERO).min(HUNDRED)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal availabilityScoreInt = availabilityScore.setScale(0, RoundingMode.HALF_UP);

        BigDecimal overallScore = capabilityScore.multiply(BigDecimal.valueOf(0.70))
                .add(availabilityScoreInt.multiply(BigDecimal.valueOf(0.30)))
                .setScale(0, RoundingMode.HALF_UP);

        String constraint = buildConstraint(
                availabilitySummary.availableFteAtStart(),
                defaultBigDecimal(role.getFteRequired(), BigDecimal.ONE)
        );
        String rationale = buildRationale(
                requiredMatched,
                requiredSkills.size(),
                desiredMatched,
                desiredSkills.size(),
                availabilitySummary.availableFteAtStart(),
                defaultBigDecimal(role.getFteRequired(), BigDecimal.ONE),
                employee.getGrade(),
                capabilityScore.intValue(),
                overallScore.intValue()
        );

        return new CandidateResult(
                employee,
                overallScore,
                capabilityScore,
                availabilityScoreInt,
                locationScore,
                skillCoverageScore,
                requiredMatched,
                requiredSkills.size(),
                desiredMatched,
                desiredSkills.size(),
                requiredEvidence.matchedSkills(),
                requiredEvidence.missingSkills(),
                desiredEvidence.matchedSkills(),
                desiredEvidence.missingSkills(),
                availabilitySummary.availableFteAtStart(),
                availabilitySummary.fteGap(),
                availabilitySummary.earliestFullAvailabilityDate(),
                null,
                rationale,
                constraint
        );
    }

    private AvailabilitySummary availabilitySummary(
            Opportunity opportunity,
            OpportunityRole role,
            Employee employee,
            List<Availability> records
    ) {
        LocalDate startDate = roleStartDate(opportunity, role);
        BigDecimal requiredFte = defaultBigDecimal(role.getFteRequired(), BigDecimal.ONE);

        List<Availability> sorted = records.stream()
                .filter(record -> record.getWeekStartDate() != null)
                .sorted(Comparator.comparing(Availability::getWeekStartDate))
                .toList();

        Availability atStart = sorted.stream()
                .filter(record -> !record.getWeekStartDate().isAfter(startDate))
                .reduce((left, right) -> right)
                .orElse(sorted.stream()
                        .filter(record -> !record.getWeekStartDate().isBefore(startDate))
                        .findFirst()
                        .orElse(null));

        BigDecimal availableAtStart = defaultBigDecimal(
                atStart != null ? atStart.getAvailableFTE() : employee.getAvailableFTECurrent(),
                BigDecimal.ZERO
        );

        LocalDate earliestFull = sorted.stream()
                .filter(record -> defaultBigDecimal(record.getAvailableFTE(), BigDecimal.ZERO).compareTo(requiredFte) >= 0)
                .map(Availability::getWeekStartDate)
                .filter(Objects::nonNull)
                .filter(date -> !date.isBefore(startDate))
                .findFirst()
                .orElse(employee.getExpectedReleaseDate());

        if (earliestFull == null) {
            earliestFull = startDate.plusDays(90);
        }

        BigDecimal fteGap = requiredFte.subtract(availableAtStart).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal coverageRatio = requiredFte.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : availableAtStart.divide(requiredFte, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal coverageScore = coverageRatio.multiply(HUNDRED);

        BigDecimal score = coverageScore.setScale(0, RoundingMode.HALF_UP);

        return new AvailabilitySummary(
                score,
                availableAtStart.setScale(2, RoundingMode.HALF_UP),
                fteGap,
                earliestFull
        );
    }

    private BigDecimal domainScore(
            Opportunity opportunity,
            OpportunityRole role,
            Employee employee,
            Profile profile,
            List<ProjectHistory> projectHistory
    ) {
        Set<String> targetDomains = normalizeValues(List.of(
                opportunity.getDomain(),
                role.getDomainExperienceRequired()
        ));
        if (targetDomains.isEmpty()) {
            return HUNDRED;
        }

        Set<String> candidateDomains = new LinkedHashSet<>();
        candidateDomains.addAll(normalizeValues(List.of(employee.getPrimaryDomain(), employee.getSecondaryDomain())));
        if (profile != null && profile.getDomainExperienceSummary() != null) {
            candidateDomains.addAll(normalizeValues(profile.getDomainExperienceSummary().keySet()));
        }
        candidateDomains.addAll(normalizeValues(projectHistory.stream().map(ProjectHistory::getDomain).toList()));

        int matched = countIntersection(targetDomains, candidateDomains);
        return ratio(matched, targetDomains.size());
    }

    private BigDecimal gradeScore(String gradePreference, String employeeGrade) {
        if (isBlank(gradePreference) || isBlank(employeeGrade)) {
            return BigDecimal.valueOf(70);
        }

        if (normalize(gradePreference).equals(normalize(employeeGrade))) {
            return HUNDRED;
        }

        Integer preferredLevel = parseLevel(gradePreference);
        Integer employeeLevel = parseLevel(employeeGrade);
        if (preferredLevel != null && employeeLevel != null) {
            int diff = Math.abs(preferredLevel - employeeLevel);
            if (diff == 1) {
                return BigDecimal.valueOf(80);
            }
            if (diff == 2) {
                return BigDecimal.valueOf(65);
            }
            return BigDecimal.valueOf(50);
        }

        return BigDecimal.valueOf(60);
    }

    private BigDecimal locationScore(Opportunity opportunity, OpportunityRole role, Employee employee) {
        Set<String> targetTokens = new LinkedHashSet<>();
        targetTokens.addAll(normalizeValues(role.getLocationPreference()));
        targetTokens.addAll(normalizeValues(List.of(
                opportunity.getCity(),
                opportunity.getCountry(),
                opportunity.getRegion()
        )));

        if (targetTokens.isEmpty()) {
            return BigDecimal.valueOf(75);
        }

        boolean remoteAcceptable = normalize(role.getFlexibilityNotes()).contains("remote")
                || normalize(role.getLocationPreference()).contains("remote");
        boolean employeeRemote = normalize(employee.getWorkMode()).contains("remote");

        Set<String> employeeCity = normalizeValues(employee.getCity());
        Set<String> employeeCountry = normalizeValues(employee.getCountry());
        Set<String> employeeRegion = normalizeValues(employee.getRegion());

        if (!employeeCity.isEmpty() && containsAny(targetTokens, employeeCity)) {
            return HUNDRED;
        }
        if (!employeeCountry.isEmpty() && containsAny(targetTokens, employeeCountry)) {
            return BigDecimal.valueOf(85);
        }
        if (!employeeRegion.isEmpty() && containsAny(targetTokens, employeeRegion)) {
            return BigDecimal.valueOf(70);
        }
        if (remoteAcceptable && employeeRemote) {
            return BigDecimal.valueOf(65);
        }
        if (employeeRemote) {
            return BigDecimal.valueOf(55);
        }

        return BigDecimal.valueOf(40);
    }

    private boolean containsAny(Set<String> haystack, Set<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal projectRelevanceScore(
            Opportunity opportunity,
            OpportunityRole role,
            List<EmployeeSkill> skills,
            List<ProjectHistory> projectHistory
    ) {
        if (projectHistory.isEmpty()) {
            return BigDecimal.valueOf(30);
        }

        Set<String> targetDomains = normalizeValues(List.of(opportunity.getDomain(), role.getDomainExperienceRequired()));
        Set<String> targetSkills = normalizeValues(merge(role.getRequiredSkills(), role.getDesiredSkills()));

        int domainHits = 0;
        int skillHits = 0;
        for (ProjectHistory history : projectHistory) {
            if (!targetDomains.isEmpty() && targetDomains.contains(normalize(history.getDomain()))) {
                domainHits++;
            }

            Set<String> historyTech = normalizeValues(history.getKeyTechnologiesOrMethods());
            skillHits += countIntersection(targetSkills, historyTech);
        }

        int directSkillHits = countIntersection(
                targetSkills,
                normalizeValues(skills.stream().map(EmployeeSkill::getSkillName).toList())
        );

        int raw = 35 + domainHits * 18 + skillHits * 6 + directSkillHits * 2;
        return BigDecimal.valueOf(Math.min(100, raw));
    }

    private String fitStatusByRank(int rank, int requiredMatched, int requiredTotal, BigDecimal fteGap) {
        BigDecimal requiredRatio = requiredTotal == 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(requiredMatched)
                        .divide(BigDecimal.valueOf(requiredTotal), 4, RoundingMode.HALF_UP);
        boolean hasGap = defaultBigDecimal(fteGap, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0;

        String base;
        if (rank == 1) {
            base = "Recommended";
        } else if (rank == 2) {
            base = requiredRatio.compareTo(BigDecimal.valueOf(0.5)) >= 0 ? "Backup" : "Gap - Alternative";
        } else {
            base = "Stretch";
        }

        if (hasGap && ("Recommended".equals(base) || "Backup".equals(base))) {
            return base + " - Availability Risk";
        }
        return base;
    }

    private String buildConstraint(BigDecimal availableFte, BigDecimal requiredFte) {
        BigDecimal avail = defaultBigDecimal(availableFte, BigDecimal.ZERO);
        BigDecimal req = defaultBigDecimal(requiredFte, BigDecimal.ZERO);
        if (req.compareTo(BigDecimal.ZERO) <= 0) {
            return "None";
        }
        if (avail.compareTo(BigDecimal.ZERO) == 0) {
            return "No availability at start; another candidate or later start is required.";
        }
        if (avail.compareTo(req) < 0) {
            return "Insufficient availability at start (" + formatFte(avail) + " of " + formatFte(req)
                    + " FTE); resolve before booking.";
        }
        return "None";
    }

    private String buildRationale(
            int requiredMatched,
            int requiredTotal,
            int desiredMatched,
            int desiredTotal,
            BigDecimal availableFte,
            BigDecimal requiredFte,
            String grade,
            int capability,
            int overall
    ) {
        String gradeLabel = isBlank(grade) ? "Unknown" : grade.trim();
        return requiredMatched + "/" + requiredTotal + " required and "
                + desiredMatched + "/" + desiredTotal + " desired skills evidenced; "
                + gradeLabel + " grade; "
                + formatFte(availableFte) + "/" + formatFte(requiredFte) + " FTE available at start; "
                + "capability " + capability + "; overall " + overall + ".";
    }

    private String formatFte(BigDecimal value) {
        return defaultBigDecimal(value, BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private void assignSequentialOverlayIds(List<OpportunityOverlay> overlays) {
        int nextSeq = nextOverlaySequence();
        for (OpportunityOverlay overlay : overlays) {
            overlay.setOverlayId(String.format("OVR-%05d", nextSeq++));
        }
    }

    private int nextOverlaySequence() {
        int maxSeq = 0;
        for (OpportunityOverlay overlay : opportunityOverlayRepository.findAll()) {
            String overlayId = overlay.getOverlayId();
            if (overlayId == null || !overlayId.startsWith("OVR-")) {
                continue;
            }
            try {
                int seq = Integer.parseInt(overlayId.substring(4));
                if (seq > maxSeq) {
                    maxSeq = seq;
                }
            } catch (NumberFormatException ignored) {
                // skip malformed ids
            }
        }
        return maxSeq + 1;
    }

    private OpportunityOverlay toOverlay(Opportunity opportunity, OpportunityRole role, CandidateResult candidate, int rank) {
        String fitStatus = fitStatusByRank(
                rank,
                candidate.requiredSkillsMatched() == null ? 0 : candidate.requiredSkillsMatched(),
                candidate.requiredSkillsTotal() == null ? 0 : candidate.requiredSkillsTotal(),
                candidate.fteGap()
        );

        return OpportunityOverlay.builder()
                .opportunityId(opportunity.getOpportunityId())
                .opportunityRoleId(role.getOpportunityRoleId())
                .employeeId(candidate.employee().getEmployeeId())
                .employeeName(candidate.employee().getEmployeeName())
                .fitStatus(fitStatus)
                .rank(rank)
                .matchScore(candidate.capabilityFitScore())
                .rationale(candidate.rationale())
                .constraint(candidate.constraint())
                .ewaStatus(candidate.employee().getEwaStatus())
                .plannerNotes(PLANNER_NOTES)
                .capabilityFitScore(candidate.capabilityFitScore())
                .availabilityFitScore(candidate.availabilityFitScore())
                .overallStaffingScore(candidate.overallStaffingScore())
                .availableFTEAtStart(candidate.availableFteAtStart())
                .fteGap(candidate.fteGap())
                .earliestFullAvailabilityDate(candidate.earliestFullAvailabilityDate())
                .requiredSkillsMatched(candidate.requiredSkillsMatched())
                .requiredSkillsTotal(candidate.requiredSkillsTotal())
                .desiredSkillsMatched(candidate.desiredSkillsMatched())
                .desiredSkillsTotal(candidate.desiredSkillsTotal())
                .build();
    }

    private List<String> buildRisks(List<OpportunityOverlay> selectedMembers) {
        LinkedHashSet<String> risks = new LinkedHashSet<>();
        for (OpportunityOverlay member : selectedMembers) {
            if (hasBlockingConstraint(member)) {
                risks.add(member.getEmployeeName() + ": " + member.getConstraint());
            } else if (normalize(member.getFitStatus()).contains("availabilityrisk")) {
                risks.add(member.getEmployeeName() + " has availability risk.");
            }
        }

        if (risks.isEmpty()) {
            risks.add("No major risks identified by rule-based engine.");
        }

        return List.copyOf(risks);
    }

    private BigDecimal calculateRiskScore(
            List<OpportunityOverlay> selectedMembers,
            int readinessDays,
            BigDecimal confidenceScore
    ) {
        BigDecimal availabilityRisk = availabilityRiskScore(selectedMembers);
        BigDecimal readinessRisk = readinessRiskScore(readinessDays);
        BigDecimal confidence = defaultBigDecimal(confidenceScore, BigDecimal.ZERO);
        BigDecimal confidenceRisk = HUNDRED.subtract(confidence).max(BigDecimal.ZERO).min(HUNDRED);
        BigDecimal constraintRisk = constraintRiskScore(selectedMembers);

        BigDecimal score = availabilityRisk.multiply(BigDecimal.valueOf(0.50))
                .add(readinessRisk.multiply(BigDecimal.valueOf(0.25)))
                .add(confidenceRisk.multiply(BigDecimal.valueOf(0.15)))
                .add(constraintRisk.multiply(BigDecimal.valueOf(0.10)));

        return score.max(BigDecimal.ZERO).min(HUNDRED).setScale(0, RoundingMode.HALF_UP);
    }

    private String riskLevel(BigDecimal riskScore) {
        BigDecimal score = defaultBigDecimal(riskScore, BigDecimal.ZERO);
        if (score.compareTo(BigDecimal.valueOf(30)) <= 0) {
            return "LOW";
        }
        if (score.compareTo(BigDecimal.valueOf(75)) <= 0) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private BigDecimal availabilityRiskScore(List<OpportunityOverlay> selectedMembers) {
        if (selectedMembers == null || selectedMembers.isEmpty()) {
            return HUNDRED;
        }

        List<BigDecimal> memberRisks = selectedMembers.stream()
                .map(member -> {
                    BigDecimal gap = defaultBigDecimal(member.getFteGap(), BigDecimal.ZERO);
                    BigDecimal available = defaultBigDecimal(member.getAvailableFTEAtStart(), BigDecimal.ZERO);
                    BigDecimal required = available.add(gap);
                    if (required.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }
                    return gap.divide(required, 4, RoundingMode.HALF_UP).multiply(HUNDRED).min(HUNDRED);
                })
                .toList();

        return average(memberRisks);
    }

    private BigDecimal readinessRiskScore(int readinessDays) {
        if (readinessDays <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(readinessDays)
                .divide(BigDecimal.valueOf(90), 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .min(HUNDRED);
    }

    private BigDecimal constraintRiskScore(List<OpportunityOverlay> selectedMembers) {
        if (selectedMembers == null || selectedMembers.isEmpty()) {
            return HUNDRED;
        }

        long blockingConstraintCount = selectedMembers.stream()
                .filter(this::hasBlockingConstraint)
                .count();

        return BigDecimal.valueOf(blockingConstraintCount)
                .divide(BigDecimal.valueOf(selectedMembers.size()), 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .min(HUNDRED);
    }

    private boolean hasBlockingConstraint(OpportunityOverlay member) {
        if (isBlank(member.getConstraint())) {
            return false;
        }

        String normalized = normalize(member.getConstraint());
        return !"none".equals(normalized) && !normalized.contains("noblockingconstraintsdetected");
    }

    private BigDecimal balancedScoreForSort(OpportunityOverlay overlay) {
        BigDecimal overallScore = defaultBigDecimal(overlay.getOverallStaffingScore(), BigDecimal.ZERO);
        BigDecimal availabilityScore = defaultBigDecimal(overlay.getAvailabilityFitScore(), BigDecimal.ZERO);
        BigDecimal riskScore = memberRiskScore(overlay);

        return overallScore.multiply(BigDecimal.valueOf(0.70))
                .add(availabilityScore.multiply(BigDecimal.valueOf(0.20)))
                .subtract(riskScore.multiply(BigDecimal.valueOf(0.10)));
    }

    private BigDecimal memberRiskScore(OpportunityOverlay overlay) {
        BigDecimal availabilityRisk = overlayAvailabilityRiskScore(overlay);
        BigDecimal overallScore = defaultBigDecimal(overlay.getOverallStaffingScore(), BigDecimal.ZERO);
        BigDecimal confidenceRisk = HUNDRED.subtract(overallScore).max(BigDecimal.ZERO).min(HUNDRED);
        BigDecimal constraintRisk = hasBlockingConstraint(overlay) ? HUNDRED : BigDecimal.ZERO;

        return availabilityRisk.multiply(BigDecimal.valueOf(0.60))
                .add(confidenceRisk.multiply(BigDecimal.valueOf(0.25)))
                .add(constraintRisk.multiply(BigDecimal.valueOf(0.15)))
                .max(BigDecimal.ZERO)
                .min(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal overlayAvailabilityRiskScore(OpportunityOverlay overlay) {
        BigDecimal gap = defaultBigDecimal(overlay.getFteGap(), BigDecimal.ZERO);
        BigDecimal available = defaultBigDecimal(overlay.getAvailableFTEAtStart(), BigDecimal.ZERO);
        BigDecimal required = available.add(gap);
        if (required.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return gap.divide(required, 4, RoundingMode.HALF_UP).multiply(HUNDRED).min(HUNDRED);
    }

    private BigDecimal optionScore(OpportunityOverlay overlay, String optionType) {
        if (OPTION_SKILL_FIT.equals(optionType)) {
            return defaultBigDecimal(overlay.getCapabilityFitScore(), overlay.getOverallStaffingScore());
        }
        if (OPTION_FASTEST.equals(optionType)) {
            return defaultBigDecimal(overlay.getAvailabilityFitScore(), overlay.getOverallStaffingScore());
        }
        return defaultBigDecimal(overlay.getOverallStaffingScore(), BigDecimal.ZERO);
    }

    private int readinessDelay(OpportunityOverlay overlay, OpportunityRole role) {
        if (role == null) {
            return 0;
        }
        LocalDate startDate = roleStartDate(null, role);
        LocalDate earliest = overlay.getEarliestFullAvailabilityDate();
        if (startDate == null || earliest == null || !earliest.isAfter(startDate)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(startDate, earliest);
    }

    private LocalDate roleStartDate(Opportunity opportunity, OpportunityRole role) {
        if (role.getStartDate() != null) {
            return role.getStartDate();
        }
        if (opportunity != null && opportunity.getExpectedStartDate() != null) {
            return opportunity.getExpectedStartDate();
        }
        return LocalDate.now();
    }

    private BigDecimal weighted(Object... parts) {
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < parts.length; index += 2) {
            BigDecimal value = defaultBigDecimal((BigDecimal) parts[index], BigDecimal.ZERO);
            BigDecimal weight = defaultBigDecimal((BigDecimal) parts[index + 1], BigDecimal.ZERO);
            total = total.add(value.multiply(weight));
        }
        return round(total);
    }

    private BigDecimal ratio(int matched, int total) {
        if (total <= 0) {
            return HUNDRED;
        }
        return BigDecimal.valueOf((double) matched / total * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal skillStrength(List<EmployeeSkill> skills, Set<String> requiredSkills, Set<String> desiredSkills) {
        Set<String> target = new HashSet<>(requiredSkills);
        target.addAll(desiredSkills);

        List<EmployeeSkill> matchedSkills = skills.stream()
                .filter(skill -> target.contains(normalize(skill.getSkillName())))
                .toList();

        if (matchedSkills.isEmpty()) {
            return BigDecimal.valueOf(35);
        }

        BigDecimal levelAvg = average(matchedSkills.stream()
                .map(skill -> BigDecimal.valueOf(skill.getSkillLevel() == null ? 3 : skill.getSkillLevel()))
                .toList());
        BigDecimal experienceAvg = average(matchedSkills.stream()
                .map(skill -> defaultBigDecimal(skill.getYearsExperience(), BigDecimal.valueOf(2)))
                .toList());

        BigDecimal levelScore = levelAvg.divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .min(HUNDRED);
        BigDecimal experienceScore = experienceAvg.divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .min(HUNDRED);

        return weighted(
                levelScore, BigDecimal.valueOf(0.65),
                experienceScore, BigDecimal.valueOf(0.35)
        );
    }

    private int countIntersection(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String value : left) {
            if (right.contains(value)) {
                count++;
            }
        }
        return count;
    }

    private Set<String> normalizeValues(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizeValues(List<String> values) {
        return normalizeValues((Collection<String>) values);
    }

    private Set<String> normalizeValues(String value) {
        if (isBlank(value)) {
            return Set.of();
        }

        String[] split = value.split("\\s*(?:,|;|\\||/)\\s*");
        Set<String> normalized = new LinkedHashSet<>();
        for (String part : split) {
            String cleaned = normalize(part);
            if (!cleaned.isBlank()) {
                normalized.add(cleaned);
            }
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String assignmentKey(String opportunityRoleId, String employeeId) {
        return safe(opportunityRoleId) + ":" + safe(employeeId);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<String> uniqueList(Collection<String> values) {
        if (values == null) {
            return List.of();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (!isBlank(value)) {
                unique.add(value.trim());
            }
        }
        return List.copyOf(unique);
    }

    private boolean parseBoolean(String value) {
        String normalized = normalize(value);
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("y") || normalized.equals("1");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Integer parseLevel(String value) {
        if (isBlank(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> merge(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return merged;
    }

    private static String roleName(OpportunityRole role) {
        return role == null ? "Unknown Role" : role.getRoleName();
    }

    private static BigDecimal scoreValueForSortCapability(OpportunityOverlay overlay) {
        return defaultBigDecimalStatic(overlay.getCapabilityFitScore(), BigDecimal.ZERO);
    }

    private static BigDecimal scoreValueForSortAvailability(OpportunityOverlay overlay) {
        return defaultBigDecimalStatic(overlay.getAvailabilityFitScore(), BigDecimal.ZERO);
    }

    private static BigDecimal scoreValueForSortOverall(OpportunityOverlay overlay) {
        return defaultBigDecimalStatic(overlay.getOverallStaffingScore(), BigDecimal.ZERO);
    }

    private static LocalDate earliestAvailabilityDateForSort(OpportunityOverlay overlay) {
        return overlay.getEarliestFullAvailabilityDate() == null ? LocalDate.MAX : overlay.getEarliestFullAvailabilityDate();
    }

    private static BigDecimal defaultBigDecimalStatic(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal defaultBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            total = total.add(value);
            count++;
        }

        if (count == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal round(BigDecimal value) {
        return defaultBigDecimal(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private record CandidateResult(
            Employee employee,
            BigDecimal overallStaffingScore,
            BigDecimal capabilityFitScore,
            BigDecimal availabilityFitScore,
            BigDecimal locationFitScore,
            BigDecimal skillCoverageScore,
            Integer requiredSkillsMatched,
            Integer requiredSkillsTotal,
            Integer desiredSkillsMatched,
            Integer desiredSkillsTotal,
            List<String> matchedRequiredSkills,
            List<String> missingRequiredSkills,
            List<String> matchedDesiredSkills,
            List<String> missingDesiredSkills,
            BigDecimal availableFteAtStart,
            BigDecimal fteGap,
            LocalDate earliestFullAvailabilityDate,
            String fitStatus,
            String rationale,
            String constraint
    ) {
    }

    private record AvailabilitySummary(
            BigDecimal score,
            BigDecimal availableFteAtStart,
            BigDecimal fteGap,
            LocalDate earliestFullAvailabilityDate
    ) {
    }

    private record SkillMatchEvidence(
            List<String> matchedSkills,
            List<String> missingSkills,
            BigDecimal coverageScore,
            int totalSkills
    ) {
    }

    private record TeamSkillEvidence(
            BigDecimal skillCoverageScore,
            List<String> matchedRequiredSkills,
            List<String> missingRequiredSkills,
            List<String> matchedDesiredSkills,
            List<String> missingDesiredSkills
    ) {
    }
}
