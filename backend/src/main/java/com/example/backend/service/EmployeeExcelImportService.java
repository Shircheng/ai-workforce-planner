package com.example.backend.service;

import com.example.backend.dto.ExcelImportResult;
import com.example.backend.entity.Allocation;
import com.example.backend.entity.Availability;
import com.example.backend.entity.Bench;
import com.example.backend.entity.Employee;
import com.example.backend.entity.EmployeeSkill;
import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityRole;
import com.example.backend.entity.Profile;
import com.example.backend.entity.ProjectHistory;
import com.example.backend.entity.SkillCatalog;
import com.example.backend.repository.AllocationRepository;
import com.example.backend.repository.AvailabilityRepository;
import com.example.backend.repository.BenchRepository;
import com.example.backend.repository.EmployeeRepository;
import com.example.backend.repository.EmployeeSkillRepository;
import com.example.backend.repository.OpportunityRepository;
import com.example.backend.repository.OpportunityRoleRepository;
import com.example.backend.repository.ProfileRepository;
import com.example.backend.repository.ProjectHistoryRepository;
import com.example.backend.repository.SkillCatalogRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmployeeExcelImportService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final AvailabilityRepository availabilityRepository;
    private final AllocationRepository allocationRepository;
    private final BenchRepository benchRepository;
    private final ProfileRepository profileRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final SkillCatalogRepository skillCatalogRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityRoleRepository opportunityRoleRepository;
    private final Resource employeeDataset;

    public EmployeeExcelImportService(
            EmployeeRepository employeeRepository,
            EmployeeSkillRepository employeeSkillRepository,
            AvailabilityRepository availabilityRepository,
            AllocationRepository allocationRepository,
            BenchRepository benchRepository,
            ProfileRepository profileRepository,
            ProjectHistoryRepository projectHistoryRepository,
            SkillCatalogRepository skillCatalogRepository,
            OpportunityRepository opportunityRepository,
            OpportunityRoleRepository opportunityRoleRepository,
            @Value("${app.import.employee-dataset}") Resource employeeDataset
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.availabilityRepository = availabilityRepository;
        this.allocationRepository = allocationRepository;
        this.benchRepository = benchRepository;
        this.profileRepository = profileRepository;
        this.projectHistoryRepository = projectHistoryRepository;
        this.skillCatalogRepository = skillCatalogRepository;
        this.opportunityRepository = opportunityRepository;
        this.opportunityRoleRepository = opportunityRoleRepository;
        this.employeeDataset = employeeDataset;
    }

    public ExcelImportResult importDefaultDataset() throws IOException {
        try (InputStream inputStream = employeeDataset.getInputStream()) {
            return importWorkbook(inputStream);
        }
    }

    public ExcelImportResult importWorkbook(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            SheetRows peopleRows = rows(workbook, "People", "Employees", "Employee");
            SheetRows skillRows = rows(workbook, "Skills", "Employee Skills", "EmployeeSkills");
            SheetRows availabilityRows = rows(workbook, "Availability", "Availability Forecast", "AvailabilityForecast");
            SheetRows allocationRows = rows(workbook, "Allocations", "Allocation");
            SheetRows benchRows = rows(workbook, "Bench", "Bench View", "BenchView");
            SheetRows profileRows = rows(workbook, "Profiles", "Profile", "Employee Profiles");
            SheetRows projectHistoryRows = rows(workbook, "Project History", "ProjectHistory", "Projects History");
            SheetRows skillCatalogRows = rows(workbook, "Skill Catalog", "SkillCatalog", "Skills Catalog");
            SheetRows opportunityRows = rows(workbook, "Opportunities", "Opportunity");
            SheetRows opportunityRoleRows = rows(workbook, "Opportunity Roles", "OpportunityRoles", "Opportunity Role");

            List<Employee> employees = mapEmployees(peopleRows.rows());
            List<EmployeeSkill> employeeSkills = mapEmployeeSkills(skillRows.rows());
            List<Availability> availability = mapAvailability(availabilityRows.rows());
            List<Allocation> allocations = mapAllocations(allocationRows.rows());
            List<Bench> bench = mapBench(benchRows.rows());
            List<Profile> profiles = mapProfiles(profileRows.rows());
            List<ProjectHistory> projectHistory = mapProjectHistory(projectHistoryRows.rows());
            List<SkillCatalog> skillCatalog = mapSkillCatalog(skillCatalogRows.rows());
            List<Opportunity> opportunities = mapOpportunities(opportunityRows.rows());
            List<OpportunityRole> opportunityRoles = mapOpportunityRoles(opportunityRoleRows.rows());

            replaceEmployeeCollections(
                    employees,
                    employeeSkills,
                    availability,
                    allocations,
                    bench,
                    profiles,
                    projectHistory,
                    skillCatalog,
                    opportunities,
                    opportunityRoles
            );

            List<String> skippedSheets = new ArrayList<>();
            addSkipped(skippedSheets, peopleRows);
            addSkipped(skippedSheets, skillRows);
            addSkipped(skippedSheets, availabilityRows);
            addSkipped(skippedSheets, allocationRows);
            addSkipped(skippedSheets, benchRows);
            addSkipped(skippedSheets, profileRows);
            addSkipped(skippedSheets, projectHistoryRows);
            addSkipped(skippedSheets, skillCatalogRows);
            addSkipped(skippedSheets, opportunityRows);
            addSkipped(skippedSheets, opportunityRoleRows);

            return new ExcelImportResult(
                    employees.size(),
                    employeeSkills.size(),
                    availability.size(),
                    allocations.size(),
                    bench.size(),
                    profiles.size(),
                    projectHistory.size(),
                    skillCatalog.size(),
                    opportunities.size(),
                    opportunityRoles.size(),
                    skippedSheets
            );
        }
    }

    private void replaceEmployeeCollections(
            List<Employee> employees,
            List<EmployeeSkill> employeeSkills,
            List<Availability> availability,
            List<Allocation> allocations,
            List<Bench> bench,
            List<Profile> profiles,
            List<ProjectHistory> projectHistory,
            List<SkillCatalog> skillCatalog,
            List<Opportunity> opportunities,
            List<OpportunityRole> opportunityRoles
    ) {
        employeeRepository.deleteAll();
        employeeSkillRepository.deleteAll();
        availabilityRepository.deleteAll();
        allocationRepository.deleteAll();
        benchRepository.deleteAll();
        profileRepository.deleteAll();
        projectHistoryRepository.deleteAll();
        skillCatalogRepository.deleteAll();
        opportunityRepository.deleteAll();
        opportunityRoleRepository.deleteAll();

        employeeRepository.saveAll(employees);
        employeeSkillRepository.saveAll(employeeSkills);
        availabilityRepository.saveAll(availability);
        allocationRepository.saveAll(allocations);
        benchRepository.saveAll(bench);
        profileRepository.saveAll(profiles);
        projectHistoryRepository.saveAll(projectHistory);
        skillCatalogRepository.saveAll(skillCatalog);
        opportunityRepository.saveAll(opportunities);
        opportunityRoleRepository.saveAll(opportunityRoles);
    }

    private List<Employee> mapEmployees(List<Map<String, String>> rows) {
        Map<String, Employee> employees = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String employeeId = text(row, "Employee_ID", "Employee ID", "EmployeeId");
            if (employeeId == null) {
                continue;
            }

            employees.putIfAbsent(employeeId, Employee.builder()
                    .employeeId(employeeId)
                    .employeeName(text(row, "Employee_Name", "Employee Name", "EmployeeName"))
                    .region(text(row, "Region"))
                    .country(text(row, "Country"))
                    .city(text(row, "City"))
                    .timezone(text(row, "Timezone", "Time Zone"))
                    .department(text(row, "Department"))
                    .discipline(text(row, "Discipline"))
                    .roleArchetype(text(row, "RoleArchetype", "Role Archetype"))
                    .grade(text(row, "Grade"))
                    .careerLevel(integer(row, "CareerLevel", "Career Level"))
                    .primaryDomain(text(row, "PrimaryDomain", "Primary Domain"))
                    .secondaryDomain(text(row, "SecondaryDomain", "Secondary Domain"))
                    .availabilityCategory(text(row, "AvailabilityCategory", "Availability Category"))
                    .currentAllocationFTE(decimal(row, "CurrentAllocationFTE", "Current Allocation FTE"))
                    .availableFTECurrent(decimal(row, "AvailableFTECurrent", "Available FTE Current"))
                    .expectedReleaseDate(date(row, "ExpectedReleaseDate", "Expected Release Date"))
                    .releaseWindow(text(row, "ReleaseWindow", "Release Window"))
                    .ewaStatus(text(row, "EWAStatus", "EWA Status"))
                    .currentAccountId(text(row, "CurrentAccount_ID", "CurrentAccountId", "Current Account ID"))
                    .currentProjectId(text(row, "CurrentProject_ID", "CurrentProjectId", "Current Project ID"))
                    .currentRole(text(row, "CurrentRole", "Current Role"))
                    .currentProjectStart(date(row, "CurrentProjectStart", "Current Project Start"))
                    .currentProjectEnd(date(row, "CurrentProjectEnd", "Current Project End"))
                    .workMode(text(row, "WorkMode", "Work Mode"))
                    .profileId(text(row, "Profile_ID", "ProfileId", "Profile ID"))
                    .build());
        }
        return new ArrayList<>(employees.values());
    }

    private List<EmployeeSkill> mapEmployeeSkills(List<Map<String, String>> rows) {
        Map<String, EmployeeSkill> skills = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String employeeId = text(row, "Employee_ID", "Employee ID", "EmployeeId");
            String skillName = text(row, "SkillName", "Skill Name");
            if (employeeId == null || skillName == null) {
                continue;
            }

            String skillRowId = text(row, "Skill_Row_ID", "SkillRowId", "Skill Row ID");
            String id = firstNonBlank(skillRowId, stableId(employeeId, skillName));
            skills.putIfAbsent(id, EmployeeSkill.builder()
                    .skillRowId(skillRowId)
                    .employeeId(employeeId)
                    .employeeName(text(row, "Employee_Name", "Employee Name", "EmployeeName"))
                    .skillName(skillName)
                    .skillCategory(text(row, "SkillCategory", "Skill Category"))
                    .skillLevel(integer(row, "SkillLevel", "Skill Level"))
                    .yearsExperience(decimal(row, "YearsExperience", "Years Experience"))
                    .lastUsedDate(date(row, "LastUsedDate", "Last Used Date"))
                    .evidenceSource(text(row, "EvidenceSource", "Evidence Source"))
                    .confidence(text(row, "Confidence"))
                    .build());
        }
        return new ArrayList<>(skills.values());
    }

    private List<Availability> mapAvailability(List<Map<String, String>> rows) {
        Map<String, Availability> availability = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String employeeId = text(row, "Employee_ID", "Employee ID", "EmployeeId");
            String weekStartDate = text(row, "WeekStartDate", "Week Start Date");
            if (employeeId == null) {
                continue;
            }

            String availabilityId = text(row, "Availability_ID", "AvailabilityId", "Availability ID");
            String id = firstNonBlank(availabilityId, stableId(employeeId, weekStartDate));
            availability.putIfAbsent(id, Availability.builder()
                    .availabilityId(availabilityId)
                    .employeeId(employeeId)
                    .employeeName(text(row, "Employee_Name", "Employee Name", "EmployeeName"))
                    .weekStartDate(date(row, "WeekStartDate", "Week Start Date"))
                    .availableFTE(decimal(row, "AvailableFTE", "Available FTE"))
                    .availabilityType(text(row, "AvailabilityType", "Availability Type"))
                    .source(text(row, "Source"))
                    .confidence(text(row, "Confidence"))
                    .ewaStatus(text(row, "EWAStatus", "EWA Status"))
                    .notes(text(row, "Notes"))
                    .build());
        }
        return new ArrayList<>(availability.values());
    }

    private List<Allocation> mapAllocations(List<Map<String, String>> rows) {
        Map<String, Allocation> allocations = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String employeeId = text(row, "Employee_ID", "Employee ID", "EmployeeId");
            String projectId = text(row, "Project_ID", "ProjectId", "Project ID");
            if (employeeId == null) {
                continue;
            }

            String allocationId = text(row, "Allocation_ID", "AllocationId", "Allocation ID");
            String id = firstNonBlank(
                    allocationId,
                    stableId(employeeId, projectId, text(row, "StartDate", "Start Date"))
            );
            allocations.putIfAbsent(id, Allocation.builder()
                    .allocationId(allocationId)
                    .employeeId(employeeId)
                    .employeeName(text(row, "Employee_Name", "Employee Name", "EmployeeName"))
                    .accountId(text(row, "Account_ID", "AccountId", "Account ID"))
                    .clientName(text(row, "ClientName", "Client Name"))
                    .clientType(text(row, "ClientType", "Client Type"))
                    .projectId(projectId)
                    .projectName(text(row, "ProjectName", "Project Name"))
                    .domain(text(row, "Domain"))
                    .roleOnProject(text(row, "RoleOnProject", "Role On Project"))
                    .allocationFTE(decimal(row, "AllocationFTE", "Allocation FTE"))
                    .startDate(date(row, "StartDate", "Start Date"))
                    .plannedEndDate(date(row, "PlannedEndDate", "Planned End Date"))
                    .allocationStatus(text(row, "AllocationStatus", "Allocation Status"))
                    .ewaStatus(text(row, "EWAStatus", "EWA Status"))
                    .lastUpdated(date(row, "LastUpdated", "Last Updated"))
                    .build());
        }
        return new ArrayList<>(allocations.values());
    }

    private List<Bench> mapBench(List<Map<String, String>> rows) {
        Map<String, Bench> bench = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String employeeId = text(row, "Employee_ID", "Employee ID", "EmployeeId");
            if (employeeId == null) {
                continue;
            }

            String benchRecordId = text(row, "Bench_Record_ID", "BenchRecordId", "Bench Record ID");
            String id = firstNonBlank(benchRecordId, stableId(employeeId, "bench"));
            bench.putIfAbsent(id, Bench.builder()
                    .benchRecordId(benchRecordId)
                    .employeeId(employeeId)
                    .employeeName(text(row, "Employee_Name", "Employee Name", "EmployeeName"))
                    .benchType(text(row, "BenchType", "Bench Type"))
                    .availabilityCategory(text(row, "AvailabilityCategory", "Availability Category"))
                    .availableFrom(date(row, "AvailableFrom", "Available From"))
                    .benchFTE(decimal(row, "BenchFTE", "Bench FTE"))
                    .benchPercent(decimal(row, "BenchPercent", "Bench Percent"))
                    .primaryDomain(text(row, "PrimaryDomain", "Primary Domain"))
                    .topSkills(list(row, "TopSkills", "Top Skills"))
                    .benchRisk(text(row, "BenchRisk", "Bench Risk"))
                    .timeOnBenchDays(integer(row, "TimeOnBenchDays", "Time On Bench Days"))
                    .suggestedAction(text(row, "SuggestedAction", "Suggested Action"))
                    .targetRoleFit(text(row, "TargetRoleFit", "Target Role Fit"))
                    .ewaActionRequired(text(row, "EWAActionRequired", "EWA Action Required"))
                    .isAlsoInPartialCapacityView(text(row, "IsAlsoInPartialCapacityView", "Is Also In Partial Capacity View"))
                    .recordUsage(text(row, "RecordUsage", "Record Usage"))
                    .build());
        }
        return new ArrayList<>(bench.values());
    }

    private List<Profile> mapProfiles(List<Map<String, String>> rows) {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String employeeId = text(row, "Employee_ID", "Employee ID", "EmployeeId");
            if (employeeId == null) {
                continue;
            }

            String profileId = text(row, "Profile_ID", "ProfileId", "Profile ID");
            String id = firstNonBlank(profileId, stableId(employeeId, "profile"));
            Map<String, String> domainExperienceSummary =
                    domainExperienceSummary(row, "DomainExperienceSummary", "Domain Experience Summary");

            profiles.putIfAbsent(id, Profile.builder()
                    .profileId(profileId)
                    .employeeId(employeeId)
                    .employeeName(text(row, "Employee_Name", "Employee Name", "EmployeeName"))
                    .profileSummary(text(row, "ProfileSummary", "Profile Summary"))
                    .keyStrengths(list(row, "KeyStrengths", "Key Strengths"))
                    .preferredWorkTypes(list(row, "PreferredWorkTypes", "Preferred Work Types"))
                    .domainExperienceSummary(domainExperienceSummary)
                    .certifications(list(row, "Certifications"))
                    .recentHighlights(list(row, "RecentHighlights", "Recent Highlights"))
                    .mobilityNotes(text(row, "MobilityNotes", "Mobility Notes"))
                    .languages(list(row, "Languages"))
                    .build());
        }
        return new ArrayList<>(profiles.values());
    }

    private List<ProjectHistory> mapProjectHistory(List<Map<String, String>> rows) {
        Map<String, ProjectHistory> projectHistory = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String employeeId = text(row, "Employee_ID", "Employee ID", "EmployeeId");
            String projectName = text(row, "ProjectName", "Project Name");
            if (employeeId == null) {
                continue;
            }

            String historyId = text(row, "History_ID", "HistoryId", "History ID");
            String id = firstNonBlank(
                    historyId,
                    stableId(employeeId, projectName, text(row, "StartDate", "Start Date"))
            );
            projectHistory.putIfAbsent(id, ProjectHistory.builder()
                    .historyId(historyId)
                    .employeeId(employeeId)
                    .employeeName(text(row, "Employee_Name", "Employee Name", "EmployeeName"))
                    .clientName(text(row, "ClientName", "Client Name"))
                    .clientType(text(row, "ClientType", "Client Type"))
                    .projectName(projectName)
                    .domain(text(row, "Domain"))
                    .role(text(row, "Role"))
                    .startDate(date(row, "StartDate", "Start Date"))
                    .endDate(date(row, "EndDate", "End Date"))
                    .keyTechnologiesOrMethods(list(row, "KeyTechnologiesOrMethods", "Key Technologies Or Methods"))
                    .responsibilities(list(row, "Responsibilities"))
                    .outcomeEvidence(list(row, "OutcomeEvidence", "Outcome Evidence"))
                    .region(text(row, "Region"))
                    .teamSize(integer(row, "TeamSize", "Team Size"))
                    .build());
        }
        return new ArrayList<>(projectHistory.values());
    }

    private List<SkillCatalog> mapSkillCatalog(List<Map<String, String>> rows) {
        Map<String, SkillCatalog> skillCatalog = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String skillName = text(row, "SkillName", "Skill Name");
            if (skillName == null) {
                continue;
            }

            String id = stableId(skillName);
            skillCatalog.putIfAbsent(id, SkillCatalog.builder()
                    .skillName(skillName)
                    .skillCategory(text(row, "SkillCategory", "Skill Category"))
                    .description(text(row, "Description"))
                    .relevantDepartments(list(row, "RelevantDepartments", "Relevant Departments"))
                    .suggestedLevelScale(skillLevelScale(row, "SuggestedLevelScale", "Suggested Level Scale"))
                    .build());
        }
        return new ArrayList<>(skillCatalog.values());
    }

    private List<Opportunity> mapOpportunities(List<Map<String, String>> rows) {
        Map<String, Opportunity> opportunities = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String opportunityId = text(row, "Opportunity_ID", "Opportunity ID", "OpportunityId");
            if (opportunityId == null) {
                continue;
            }

            opportunities.putIfAbsent(opportunityId, Opportunity.builder()
                    .opportunityId(opportunityId)
                    .opportunityName(text(row, "Opportunity_Name", "Opportunity Name", "OpportunityName"))
                    .clientName(text(row, "Client_Name", "Client Name", "ClientName"))
                    .clientType(text(row, "Client_Type", "Client Type", "ClientType"))
                    .region(text(row, "Region"))
                    .country(text(row, "Country"))
                    .city(text(row, "City"))
                    .domain(text(row, "Domain"))
                    .stage(text(row, "Stage"))
                    .probability(decimal(row, "Probability"))
                    .expectedStartDate(date(row, "ExpectedStartDate", "Expected Start Date"))
                    .durationWeeks(integer(row, "DurationWeeks", "Duration Weeks"))
                    .commercialPriority(text(row, "CommercialPriority", "Commercial Priority"))
                    .deliveryRisk(text(row, "DeliveryRisk", "Delivery Risk"))
                    .opportunityBrief(text(row, "OpportunityBrief", "Opportunity Brief"))
                    .timezonePreference(text(row, "TimezonePreference", "Timezone Preference"))
                    .build());
        }
        return new ArrayList<>(opportunities.values());
    }

    private List<OpportunityRole> mapOpportunityRoles(List<Map<String, String>> rows) {
        Map<String, OpportunityRole> roles = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String opportunityRoleId =
                    text(row, "Opportunity_Role_ID", "Opportunity Role ID", "OpportunityRoleId");
            if (opportunityRoleId == null) {
                continue;
            }

            roles.putIfAbsent(opportunityRoleId, OpportunityRole.builder()
                    .opportunityRoleId(opportunityRoleId)
                    .opportunityId(text(row, "Opportunity_ID", "Opportunity ID", "OpportunityId"))
                    .roleName(text(row, "RoleName", "Role Name"))
                    .disciplineOrDepartment(text(row, "DisciplineOrDepartment", "Discipline Or Department"))
                    .gradePreference(text(row, "GradePreference", "Grade Preference"))
                    .requiredSkills(list(row, "RequiredSkills", "Required Skills"))
                    .desiredSkills(list(row, "DesiredSkills", "Desired Skills"))
                    .domainExperienceRequired(text(row, "DomainExperienceRequired", "Domain Experience Required"))
                    .locationPreference(text(row, "LocationPreference", "Location Preference"))
                    .startDate(date(row, "StartDate", "Start Date"))
                    .durationWeeks(integer(row, "DurationWeeks", "Duration Weeks"))
                    .fteRequired(decimal(row, "FTERequired", "FTE Required"))
                    .priority(text(row, "Priority"))
                    .flexibilityNotes(text(row, "FlexibilityNotes", "Flexibility Notes"))
                    .minimumIndividualFTE(decimal(row, "MinimumIndividualFTE", "Minimum Individual FTE"))
                    .canCombineCandidates(text(row, "CanCombineCandidates", "Can Combine Candidates"))
                    .build());
        }
        return new ArrayList<>(roles.values());
    }

    private SheetRows rows(Workbook workbook, String... sheetNames) {
        Sheet sheet = findSheet(workbook, sheetNames);
        String label = sheetNames[0];
        if (sheet == null) {
            return new SheetRows(label, true, List.of());
        }

        Row headerRow = firstNonEmptyRow(sheet);
        if (headerRow == null) {
            return new SheetRows(sheet.getSheetName(), true, List.of());
        }

        DataFormatter formatter = new DataFormatter(Locale.US);
        Map<Integer, String> headers = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = clean(formatter.formatCellValue(cell));
            if (header != null) {
                headers.put(cell.getColumnIndex(), normalizeKey(header));
            }
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> header : headers.entrySet()) {
                String value = cellText(row.getCell(header.getKey()), formatter);
                if (value != null) {
                    values.put(header.getValue(), value);
                }
            }
            if (!values.isEmpty()) {
                rows.add(values);
            }
        }

        return new SheetRows(sheet.getSheetName(), rows.isEmpty(), rows);
    }

    private Sheet findSheet(Workbook workbook, String... sheetNames) {
        List<String> wanted = List.of(sheetNames).stream().map(this::normalizeKey).toList();
        for (Sheet sheet : workbook) {
            String current = normalizeKey(sheet.getSheetName());
            if (wanted.contains(current)) {
                return sheet;
            }
        }
        for (Sheet sheet : workbook) {
            String current = normalizeKey(sheet.getSheetName());
            if (wanted.stream().anyMatch(name -> current.contains(name) || name.contains(current))) {
                return sheet;
            }
        }
        return null;
    }

    private Row firstNonEmptyRow(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.US);
        for (Row row : sheet) {
            boolean hasValue = false;
            for (Cell cell : row) {
                if (cellText(cell, formatter) != null) {
                    hasValue = true;
                    break;
                }
            }
            if (hasValue) {
                return row;
            }
        }
        return null;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (isDateCell(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return clean(formatter.formatCellValue(cell));
    }

    private boolean isDateCell(Cell cell) {
        CellType cellType = cell.getCellType();
        boolean numericCell = cellType == CellType.NUMERIC
                || (cellType == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC);
        return numericCell && DateUtil.isCellDateFormatted(cell);
    }

    private String text(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            String value = clean(row.get(normalizeKey(alias)));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal decimal(Map<String, String> row, String... aliases) {
        String value = text(row, aliases);
        if (value == null) {
            return null;
        }

        boolean isPercent = value.endsWith("%");
        String normalized = value.replace("%", "").replace(",", "").trim();
        if (normalized.isBlank()) {
            return null;
        }

        try {
            BigDecimal number = new BigDecimal(normalized);
            return isPercent ? number.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP) : number;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate date(Map<String, String> row, String... aliases) {
        String value = text(row, aliases);
        if (value == null) {
            return null;
        }

        for (DateTimeFormatter formatter : dateFormatters()) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next known Excel export format.
            }
        }
        return null;
    }

    private List<DateTimeFormatter> dateFormatters() {
        return List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("M-d-yyyy"),
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        );
    }

    private List<String> list(Map<String, String> row, String... aliases) {
        String value = text(row, aliases);
        if (value == null) {
            return List.of();
        }
        return listFromValues(value.split("\\s*(?:;|,|\\||\\n)\\s*"));
    }

    private Map<String, String> domainExperienceSummary(Map<String, String> row, String... aliases) {
        String value = text(row, aliases);
        if (value == null) {
            return Map.of();
        }

        Map<String, String> summary = new LinkedHashMap<>();
        for (String part : value.split("\\s*;\\s*")) {
            String[] keyValue = part.split("\\s*:\\s*", 2);
            if (keyValue.length != 2) {
                continue;
            }

            String key = normalizeDomainExperienceKey(keyValue[0]);
            String parsedValue = clean(keyValue[1].replaceAll("\\.$", ""));
            if (key != null && parsedValue != null) {
                summary.put(key, parsedValue);
            }
        }

        if (summary.isEmpty()) {
            summary.put("raw", value);
        }
        return summary;
    }

    private Map<Integer, String> skillLevelScale(Map<String, String> row, String... aliases) {
        List<String> values = list(row, aliases);
        Map<Integer, String> scales = new LinkedHashMap<>();

        for (String value : values) {
            Matcher matcher = Pattern.compile("^(\\d+)\\s*[-:.]?\\s*(.+)$").matcher(value);
            if (matcher.matches()) {
                scales.put(Integer.parseInt(matcher.group(1)), clean(matcher.group(2)));
            }
        }

        return scales.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(
                        LinkedHashMap::new,
                        (result, entry) -> result.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll
                );
    }

    private String normalizeDomainExperienceKey(String key) {
        String normalized = normalizeKey(key);
        if ("primarydomain".equals(normalized)) {
            return "primary";
        }
        if ("secondarydomain".equals(normalized)) {
            return "secondary";
        }
        return clean(key);
    }

    private List<String> listFromValues(String... values) {
        return Arrays.stream(values)
                .map(this::clean)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Integer integer(Map<String, String> row, String... aliases) {
        BigDecimal value = decimal(row, aliases);
        return value == null ? null : value.setScale(0, RoundingMode.DOWN).intValue();
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private String stableId(String... parts) {
        return Arrays.stream(parts)
                .map(this::clean)
                .filter(Objects::nonNull)
                .map(part -> part.replaceAll("\\s+", "-").toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + "::" + right)
                .orElse("unknown");
    }

    private void addSkipped(List<String> skippedSheets, SheetRows rows) {
        if (rows.skipped()) {
            skippedSheets.add(rows.sheetName());
        }
    }

    private record SheetRows(String sheetName, boolean skipped, List<Map<String, String>> rows) {
    }
}
