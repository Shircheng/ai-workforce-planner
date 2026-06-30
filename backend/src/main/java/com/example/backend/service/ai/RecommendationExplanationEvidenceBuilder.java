package com.example.backend.service.ai;

import com.example.backend.entity.Allocation;
import com.example.backend.entity.Availability;
import com.example.backend.entity.Bench;
import com.example.backend.entity.Employee;
import com.example.backend.entity.EmployeeSkill;
import com.example.backend.entity.EwaRequest;
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
import com.example.backend.repository.EwaRequestRepository;
import com.example.backend.repository.OpportunityOverlayRepository;
import com.example.backend.repository.OpportunityRepository;
import com.example.backend.repository.OpportunityRoleRepository;
import com.example.backend.repository.ProfileRepository;
import com.example.backend.repository.ProjectHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecommendationExplanationEvidenceBuilder {

    private static final int MAX_SKILL_EVIDENCE = 14;
    private static final int MAX_PROJECT_HISTORY = 4;
    private static final int MAX_ALLOCATIONS = 3;
    private static final int MAX_AVAILABILITY_WEEKS = 8;
    private static final int MAX_BENCH_RECORDS = 2;

    private final OpportunityRepository opportunityRepository;
    private final OpportunityRoleRepository opportunityRoleRepository;
    private final OpportunityOverlayRepository opportunityOverlayRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final ProfileRepository profileRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final AllocationRepository allocationRepository;
    private final AvailabilityRepository availabilityRepository;
    private final BenchRepository benchRepository;
    private final EwaRequestRepository ewaRequestRepository;

    public RecommendationExplanationEvidenceBuilder(
            OpportunityRepository opportunityRepository,
            OpportunityRoleRepository opportunityRoleRepository,
            OpportunityOverlayRepository opportunityOverlayRepository,
            EmployeeRepository employeeRepository,
            EmployeeSkillRepository employeeSkillRepository,
            ProfileRepository profileRepository,
            ProjectHistoryRepository projectHistoryRepository,
            AllocationRepository allocationRepository,
            AvailabilityRepository availabilityRepository,
            BenchRepository benchRepository,
            EwaRequestRepository ewaRequestRepository
    ) {
        this.opportunityRepository = opportunityRepository;
        this.opportunityRoleRepository = opportunityRoleRepository;
        this.opportunityOverlayRepository = opportunityOverlayRepository;
        this.employeeRepository = employeeRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.profileRepository = profileRepository;
        this.projectHistoryRepository = projectHistoryRepository;
        this.allocationRepository = allocationRepository;
        this.availabilityRepository = availabilityRepository;
        this.benchRepository = benchRepository;
        this.ewaRequestRepository = ewaRequestRepository;
    }

    public RecommendationExplanationEvidence build(RecommendationRun run) {
        Opportunity opportunity = opportunityRepository.findByOpportunityId(run.getOpportunityId()).orElse(null);
        Map<String, OpportunityRole> rolesById = opportunityRoleRepository.findByOpportunityId(run.getOpportunityId())
                .stream()
                .collect(Collectors.toMap(
                        OpportunityRole::getOpportunityRoleId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<OpportunityOverlay> overlays = opportunityOverlayRepository.findByOpportunityId(run.getOpportunityId());

        List<OptionEvidence> options = new ArrayList<>();
        int optionIndex = 1;
        for (RecommendationRunOption option : safeList(run.getOptions())) {
            String optionId = run.getRecommendationRunId() + "-OPTION-" + optionIndex;
            options.add(buildOption(run, option, optionId, rolesById, overlays));
            optionIndex++;
        }

        return new RecommendationExplanationEvidence(
                run.getRecommendationRunId(),
                run.getOpportunityId(),
                firstNonBlank(run.getOpportunityName(), opportunity == null ? null : opportunity.getOpportunityName()),
                date(run.getGeneratedAt()),
                opportunityEvidence(opportunity),
                options
        );
    }

    private OptionEvidence buildOption(
            RecommendationRun run,
            RecommendationRunOption option,
            String optionId,
            Map<String, OpportunityRole> rolesById,
            List<OpportunityOverlay> overlays
    ) {
        List<MemberEvidence> members = new ArrayList<>();
        for (RecommendationRunMember member : safeList(option.getMembers())) {
            OpportunityRole role = roleFor(member.getOpportunityRoleId(), rolesById);
            members.add(buildMember(run, member, role, overlays));
        }

        List<String> missingSkills = safeList(option.getMissingRequiredSkills()).isEmpty()
                ? members.stream()
                        .flatMap(member -> member.missingRequiredSkills().stream())
                        .distinct()
                        .toList()
                : safeList(option.getMissingRequiredSkills());

        return new OptionEvidence(
                optionId,
                option.getOptionType(),
                optionName(option.getOptionType()),
                option.getConfidenceScore(),
                option.getRiskScore(),
                option.getRiskLevel(),
                option.getReadinessDays(),
                option.getSelectedMemberCount(),
                safeList(option.getRisks()),
                missingSkills,
                members
        );
    }

    private MemberEvidence buildMember(
            RecommendationRun run,
            RecommendationRunMember runMember,
            OpportunityRole role,
            List<OpportunityOverlay> overlays
    ) {
        Employee employee = employeeRepository.findByEmployeeId(runMember.getEmployeeId()).orElse(null);
        OpportunityOverlay overlay = overlayFor(overlays, runMember);
        List<EmployeeSkill> skills = employeeSkillRepository.findByEmployeeId(runMember.getEmployeeId());
        Profile profile = profileRepository.findByEmployeeId(runMember.getEmployeeId()).orElse(null);
        List<ProjectHistory> history = projectHistoryRepository.findByEmployeeId(runMember.getEmployeeId())
                .stream()
                .sorted(Comparator.comparing(ProjectHistory::getEndDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_PROJECT_HISTORY)
                .toList();
        List<Allocation> allocations = allocationRepository.findByEmployeeId(runMember.getEmployeeId())
                .stream()
                .sorted(Comparator.comparing(Allocation::getPlannedEndDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_ALLOCATIONS)
                .toList();
        List<Availability> availability = availabilityRepository.findByEmployeeId(runMember.getEmployeeId())
                .stream()
                .sorted(Comparator.comparing(Availability::getWeekStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_AVAILABILITY_WEEKS)
                .toList();
        List<Bench> bench = benchRepository.findByEmployeeId(runMember.getEmployeeId())
                .stream()
                .limit(MAX_BENCH_RECORDS)
                .toList();
        List<EwaRequest> ewaRequests = ewaRequestRepository.findByOpportunityIdAndOpportunityRoleIdAndEmployeeId(
                run.getOpportunityId(),
                runMember.getOpportunityRoleId(),
                runMember.getEmployeeId()
        );

        SkillMatch requiredSkillMatch = skillMatch(role == null ? List.of() : role.getRequiredSkills(), skills);
        SkillMatch desiredSkillMatch = skillMatch(role == null ? List.of() : role.getDesiredSkills(), skills);
        BigDecimal availableFteAtStart = firstNonNull(
                overlay == null ? null : overlay.getAvailableFTEAtStart(),
                runMember.getAvailableFteAtStart()
        );
        BigDecimal fteGap = firstNonNull(overlay == null ? null : overlay.getFteGap(), runMember.getFteGap());
        BigDecimal fteRequired = firstNonNull(role == null ? null : role.getFteRequired(), add(availableFteAtStart, fteGap));
        String ewaStatus = firstNonBlank(
                firstEwaValue(ewaRequests, EwaRequest::getEwaStatus),
                overlay == null ? null : overlay.getEwaStatus(),
                employee == null ? null : employee.getEwaStatus()
        );
        String blockingReason = firstNonBlank(
                firstEwaValue(ewaRequests, EwaRequest::getBlockingReason),
                isBlockingConstraint(runMember.getConstraint()) ? runMember.getConstraint() : null,
                overlay != null && isBlockingConstraint(overlay.getConstraint()) ? overlay.getConstraint() : null
        );
        String canSplitRole = firstNonBlank(
                firstEwaValue(ewaRequests, EwaRequest::getCanSplitRole),
                role == null ? null : role.getCanCombineCandidates()
        );

        return new MemberEvidence(
                runMember.getEmployeeId(),
                firstNonBlank(runMember.getEmployeeName(), employee == null ? null : employee.getEmployeeName()),
                runMember.getOpportunityRoleId(),
                firstNonBlank(runMember.getRoleName(), role == null ? null : role.getRoleName()),
                runMember.getRank(),
                runMember.getFitStatus(),
                scores(runMember, overlay),
                roleEvidence(role, fteRequired),
                employeeEvidence(employee),
                requiredSkillMatch.matched(),
                requiredSkillMatch.missing(),
                desiredSkillMatch.matched(),
                desiredSkillMatch.missing(),
                skillEvidence(skills, role),
                profileEvidence(profile),
                projectHistoryEvidence(history),
                allocationEvidence(allocations),
                availabilityEvidence(availability),
                benchEvidence(bench),
                overlayEvidence(overlay, runMember),
                ewaEvidence(ewaRequests),
                availableFteAtStart,
                fteRequired,
                fteGap,
                date(firstNonNull(
                        overlay == null ? null : overlay.getEarliestFullAvailabilityDate(),
                        runMember.getEarliestFullAvailabilityDate()
                )),
                ewaStatus,
                blockingReason,
                canSplitRole,
                runMember.getRationale(),
                runMember.getConstraint()
        );
    }

    private OpportunityRole roleFor(String opportunityRoleId, Map<String, OpportunityRole> rolesById) {
        if (!StringUtils.hasText(opportunityRoleId)) {
            return null;
        }
        OpportunityRole role = rolesById.get(opportunityRoleId);
        if (role != null) {
            return role;
        }
        return opportunityRoleRepository.findByOpportunityRoleId(opportunityRoleId).orElse(null);
    }

    private OpportunityOverlay overlayFor(List<OpportunityOverlay> overlays, RecommendationRunMember member) {
        return overlays.stream()
                .filter(overlay -> Objects.equals(overlay.getOpportunityRoleId(), member.getOpportunityRoleId()))
                .filter(overlay -> Objects.equals(overlay.getEmployeeId(), member.getEmployeeId()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> opportunityEvidence(Opportunity opportunity) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (opportunity == null) {
            return values;
        }
        put(values, "opportunityName", opportunity.getOpportunityName());
        put(values, "clientName", opportunity.getClientName());
        put(values, "clientType", opportunity.getClientType());
        put(values, "region", opportunity.getRegion());
        put(values, "country", opportunity.getCountry());
        put(values, "city", opportunity.getCity());
        put(values, "domain", opportunity.getDomain());
        put(values, "stage", opportunity.getStage());
        put(values, "probability", opportunity.getProbability());
        put(values, "expectedStartDate", date(opportunity.getExpectedStartDate()));
        put(values, "durationWeeks", opportunity.getDurationWeeks());
        put(values, "commercialPriority", opportunity.getCommercialPriority());
        put(values, "deliveryRisk", opportunity.getDeliveryRisk());
        put(values, "opportunityBrief", opportunity.getOpportunityBrief());
        put(values, "timezonePreference", opportunity.getTimezonePreference());
        return values;
    }

    private Map<String, Object> roleEvidence(OpportunityRole role, BigDecimal fteRequired) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (role == null) {
            put(values, "fteRequired", fteRequired);
            return values;
        }
        put(values, "roleName", role.getRoleName());
        put(values, "disciplineOrDepartment", role.getDisciplineOrDepartment());
        put(values, "gradePreference", role.getGradePreference());
        put(values, "requiredSkills", role.getRequiredSkills());
        put(values, "desiredSkills", role.getDesiredSkills());
        put(values, "domainExperienceRequired", role.getDomainExperienceRequired());
        put(values, "locationPreference", role.getLocationPreference());
        put(values, "startDate", date(role.getStartDate()));
        put(values, "durationWeeks", role.getDurationWeeks());
        put(values, "fteRequired", fteRequired);
        put(values, "priority", role.getPriority());
        put(values, "flexibilityNotes", role.getFlexibilityNotes());
        put(values, "minimumIndividualFTE", role.getMinimumIndividualFTE());
        put(values, "canCombineCandidates", role.getCanCombineCandidates());
        return values;
    }

    private Map<String, Object> employeeEvidence(Employee employee) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (employee == null) {
            return values;
        }
        put(values, "employeeName", employee.getEmployeeName());
        put(values, "grade", employee.getGrade());
        put(values, "careerLevel", employee.getCareerLevel());
        put(values, "roleArchetype", employee.getRoleArchetype());
        put(values, "department", employee.getDepartment());
        put(values, "discipline", employee.getDiscipline());
        put(values, "region", employee.getRegion());
        put(values, "country", employee.getCountry());
        put(values, "city", employee.getCity());
        put(values, "timezone", employee.getTimezone());
        put(values, "primaryDomain", employee.getPrimaryDomain());
        put(values, "secondaryDomain", employee.getSecondaryDomain());
        put(values, "availabilityCategory", employee.getAvailabilityCategory());
        put(values, "currentAllocationFTE", employee.getCurrentAllocationFTE());
        put(values, "availableFTECurrent", employee.getAvailableFTECurrent());
        put(values, "expectedReleaseDate", date(employee.getExpectedReleaseDate()));
        put(values, "releaseWindow", employee.getReleaseWindow());
        put(values, "ewaStatus", employee.getEwaStatus());
        put(values, "currentRole", employee.getCurrentRole());
        put(values, "currentProjectStart", date(employee.getCurrentProjectStart()));
        put(values, "currentProjectEnd", date(employee.getCurrentProjectEnd()));
        put(values, "workMode", employee.getWorkMode());
        return values;
    }

    private Map<String, Object> scores(RecommendationRunMember runMember, OpportunityOverlay overlay) {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "rank", runMember.getRank());
        put(values, "matchScore", firstNonNull(overlay == null ? null : overlay.getMatchScore(), runMember.getMatchScore()));
        put(values, "capabilityFitScore", firstNonNull(overlay == null ? null : overlay.getCapabilityFitScore(), runMember.getCapabilityFitScore()));
        put(values, "availabilityFitScore", firstNonNull(overlay == null ? null : overlay.getAvailabilityFitScore(), runMember.getAvailabilityFitScore()));
        put(values, "overallStaffingScore", firstNonNull(overlay == null ? null : overlay.getOverallStaffingScore(), runMember.getOverallStaffingScore()));
        return values;
    }

    private Map<String, Object> profileEvidence(Profile profile) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (profile == null) {
            return values;
        }
        put(values, "profileSummary", profile.getProfileSummary());
        put(values, "keyStrengths", profile.getKeyStrengths());
        put(values, "preferredWorkTypes", profile.getPreferredWorkTypes());
        put(values, "domainExperienceSummary", profile.getDomainExperienceSummary());
        put(values, "certifications", profile.getCertifications());
        put(values, "recentHighlights", profile.getRecentHighlights());
        put(values, "mobilityNotes", profile.getMobilityNotes());
        put(values, "languages", profile.getLanguages());
        return values;
    }

    private Map<String, Object> overlayEvidence(OpportunityOverlay overlay, RecommendationRunMember runMember) {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "fitStatus", firstNonBlank(overlay == null ? null : overlay.getFitStatus(), runMember.getFitStatus()));
        put(values, "rank", firstNonNull(overlay == null ? null : overlay.getRank(), runMember.getRank()));
        put(values, "rationale", firstNonBlank(overlay == null ? null : overlay.getRationale(), runMember.getRationale()));
        put(values, "constraint", firstNonBlank(overlay == null ? null : overlay.getConstraint(), runMember.getConstraint()));
        put(values, "ewaStatus", overlay == null ? null : overlay.getEwaStatus());
        put(values, "plannerNotes", overlay == null ? null : overlay.getPlannerNotes());
        put(values, "availableFTEAtStart", firstNonNull(overlay == null ? null : overlay.getAvailableFTEAtStart(), runMember.getAvailableFteAtStart()));
        put(values, "fteGap", firstNonNull(overlay == null ? null : overlay.getFteGap(), runMember.getFteGap()));
        put(values, "earliestFullAvailabilityDate", date(firstNonNull(
                overlay == null ? null : overlay.getEarliestFullAvailabilityDate(),
                runMember.getEarliestFullAvailabilityDate()
        )));
        put(values, "requiredSkillsMatched", overlay == null ? null : overlay.getRequiredSkillsMatched());
        put(values, "requiredSkillsTotal", overlay == null ? null : overlay.getRequiredSkillsTotal());
        put(values, "desiredSkillsMatched", overlay == null ? null : overlay.getDesiredSkillsMatched());
        put(values, "desiredSkillsTotal", overlay == null ? null : overlay.getDesiredSkillsTotal());
        return values;
    }

    private List<Map<String, Object>> skillEvidence(List<EmployeeSkill> skills, OpportunityRole role) {
        Set<String> roleSkills = new LinkedHashSet<>();
        if (role != null) {
            safeList(role.getRequiredSkills()).forEach(skill -> roleSkills.add(normalize(skill)));
            safeList(role.getDesiredSkills()).forEach(skill -> roleSkills.add(normalize(skill)));
        }
        return skills.stream()
                .sorted(Comparator
                        .comparing((EmployeeSkill skill) -> roleSkills.contains(normalize(skill.getSkillName()))).reversed()
                        .thenComparing(EmployeeSkill::getSkillLevel, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_SKILL_EVIDENCE)
                .map(skill -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    put(values, "skillName", skill.getSkillName());
                    put(values, "skillCategory", skill.getSkillCategory());
                    put(values, "skillLevel", skill.getSkillLevel());
                    put(values, "yearsExperience", skill.getYearsExperience());
                    put(values, "lastUsedDate", date(skill.getLastUsedDate()));
                    put(values, "evidenceSource", skill.getEvidenceSource());
                    put(values, "confidence", skill.getConfidence());
                    return values;
                })
                .toList();
    }

    private List<Map<String, Object>> projectHistoryEvidence(List<ProjectHistory> history) {
        return history.stream()
                .map(item -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    put(values, "projectName", item.getProjectName());
                    put(values, "clientName", item.getClientName());
                    put(values, "clientType", item.getClientType());
                    put(values, "domain", item.getDomain());
                    put(values, "role", item.getRole());
                    put(values, "startDate", date(item.getStartDate()));
                    put(values, "endDate", date(item.getEndDate()));
                    put(values, "keyTechnologiesOrMethods", item.getKeyTechnologiesOrMethods());
                    put(values, "responsibilities", item.getResponsibilities());
                    put(values, "outcomeEvidence", item.getOutcomeEvidence());
                    put(values, "region", item.getRegion());
                    put(values, "teamSize", item.getTeamSize());
                    return values;
                })
                .toList();
    }

    private List<Map<String, Object>> allocationEvidence(List<Allocation> allocations) {
        return allocations.stream()
                .map(item -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    put(values, "clientName", item.getClientName());
                    put(values, "clientType", item.getClientType());
                    put(values, "projectName", item.getProjectName());
                    put(values, "domain", item.getDomain());
                    put(values, "roleOnProject", item.getRoleOnProject());
                    put(values, "allocationFTE", item.getAllocationFTE());
                    put(values, "startDate", date(item.getStartDate()));
                    put(values, "plannedEndDate", date(item.getPlannedEndDate()));
                    put(values, "allocationStatus", item.getAllocationStatus());
                    put(values, "ewaStatus", item.getEwaStatus());
                    return values;
                })
                .toList();
    }

    private List<Map<String, Object>> availabilityEvidence(List<Availability> availability) {
        return availability.stream()
                .map(item -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    put(values, "weekStartDate", date(item.getWeekStartDate()));
                    put(values, "availableFTE", item.getAvailableFTE());
                    put(values, "availabilityType", item.getAvailabilityType());
                    put(values, "source", item.getSource());
                    put(values, "confidence", item.getConfidence());
                    put(values, "ewaStatus", item.getEwaStatus());
                    put(values, "notes", item.getNotes());
                    return values;
                })
                .toList();
    }

    private List<Map<String, Object>> benchEvidence(List<Bench> bench) {
        return bench.stream()
                .map(item -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    put(values, "benchType", item.getBenchType());
                    put(values, "availabilityCategory", item.getAvailabilityCategory());
                    put(values, "availableFrom", date(item.getAvailableFrom()));
                    put(values, "benchFTE", item.getBenchFTE());
                    put(values, "benchPercent", item.getBenchPercent());
                    put(values, "primaryDomain", item.getPrimaryDomain());
                    put(values, "topSkills", item.getTopSkills());
                    put(values, "benchRisk", item.getBenchRisk());
                    put(values, "timeOnBenchDays", item.getTimeOnBenchDays());
                    put(values, "suggestedAction", item.getSuggestedAction());
                    put(values, "targetRoleFit", item.getTargetRoleFit());
                    put(values, "ewaActionRequired", item.getEwaActionRequired());
                    put(values, "isAlsoInPartialCapacityView", item.getIsAlsoInPartialCapacityView());
                    put(values, "recordUsage", item.getRecordUsage());
                    return values;
                })
                .toList();
    }

    private List<Map<String, Object>> ewaEvidence(List<EwaRequest> requests) {
        return requests.stream()
                .map(item -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    put(values, "ewaRequestId", item.getEwaRequestId());
                    put(values, "requestType", item.getRequestType());
                    put(values, "ewaStatus", item.getEwaStatus());
                    put(values, "requestedFTE", item.getRequestedFTE());
                    put(values, "proposedStartDate", date(item.getProposedStartDate()));
                    put(values, "proposedEndDate", date(item.getProposedEndDate()));
                    put(values, "approvalRequired", item.getApprovalRequired());
                    put(values, "bookingOwner", item.getBookingOwner());
                    put(values, "blockingReason", item.getBlockingReason());
                    put(values, "availableFTEAtStart", item.getAvailableFTEAtStart());
                    put(values, "fteGap", item.getFteGap());
                    put(values, "canSplitRole", item.getCanSplitRole());
                    put(values, "earliestFullAvailabilityDate", date(item.getEarliestFullAvailabilityDate()));
                    put(values, "lastUpdated", date(item.getLastUpdated()));
                    put(values, "notes", item.getNotes());
                    return values;
                })
                .toList();
    }

    private SkillMatch skillMatch(List<String> requiredSkills, List<EmployeeSkill> employeeSkills) {
        Map<String, String> employeeSkillNames = employeeSkills.stream()
                .map(EmployeeSkill::getSkillName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toMap(this::normalize, Function.identity(), (first, ignored) -> first));

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String requiredSkill : safeList(requiredSkills)) {
            String normalized = normalize(requiredSkill);
            if (employeeSkillNames.containsKey(normalized)) {
                matched.add(requiredSkill);
            } else {
                missing.add(requiredSkill);
            }
        }
        return new SkillMatch(List.copyOf(matched), List.copyOf(missing));
    }

    private String firstEwaValue(List<EwaRequest> requests, Function<EwaRequest, String> accessor) {
        return requests.stream()
                .map(accessor)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String optionName(String optionType) {
        if (!StringUtils.hasText(optionType)) {
            return "Recommendation option";
        }
        String cleaned = optionType.replace('_', ' ').replace('-', ' ').trim().toLowerCase();
        StringBuilder result = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return;
        }
        values.put(key, value);
    }

    private boolean isBlockingConstraint(String constraint) {
        if (!StringUtils.hasText(constraint)) {
            return false;
        }
        String normalized = normalize(constraint);
        return !"none".equals(normalized) && !normalized.contains("noblockingconstraintsdetected");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal add(BigDecimal first, BigDecimal second) {
        if (first == null && second == null) {
            return null;
        }
        return Optional.ofNullable(first).orElse(BigDecimal.ZERO)
                .add(Optional.ofNullable(second).orElse(BigDecimal.ZERO));
    }

    private String date(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private String date(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record SkillMatch(List<String> matched, List<String> missing) {
    }

    public record RecommendationExplanationEvidence(
            String recommendationRunId,
            String opportunityId,
            String opportunityName,
            String recommendationGeneratedAt,
            Map<String, Object> opportunity,
            List<OptionEvidence> options
    ) {
    }

    public record OptionEvidence(
            String optionId,
            String optionType,
            String optionName,
            BigDecimal confidenceScore,
            BigDecimal riskScore,
            String riskLevel,
            Integer readinessDays,
            Integer selectedMemberCount,
            List<String> risks,
            List<String> missingSkills,
            List<MemberEvidence> members
    ) {
    }

    public record MemberEvidence(
            String employeeId,
            String employeeName,
            String opportunityRoleId,
            String roleName,
            Integer rank,
            String fitStatus,
            Map<String, Object> scores,
            Map<String, Object> role,
            Map<String, Object> employee,
            List<String> matchedRequiredSkills,
            List<String> missingRequiredSkills,
            List<String> matchedDesiredSkills,
            List<String> missingDesiredSkills,
            List<Map<String, Object>> skillEvidence,
            Map<String, Object> profile,
            List<Map<String, Object>> projectHistoryEvidence,
            List<Map<String, Object>> currentAllocations,
            List<Map<String, Object>> availabilityCalendar,
            List<Map<String, Object>> benchEvidence,
            Map<String, Object> overlayEvidence,
            List<Map<String, Object>> ewaRequests,
            BigDecimal availableFteAtStart,
            BigDecimal fteRequired,
            BigDecimal fteGap,
            String earliestFullAvailabilityDate,
            String ewaStatus,
            String blockingReason,
            String canSplitRole,
            String rationale,
            String constraint
    ) {
    }
}
