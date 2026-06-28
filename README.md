# AI-Workforce-Planner

AI-Workforce-Planner is an AI-assisted workforce planning application built for the InSync Hackathon.

The application helps workforce planners, sales leaders, and delivery leaders explore available talent, create staffing opportunities, generate evidence-backed team recommendations, compare staffing options, and prepare final recommendations for EWA review.

The goal is not to replace human judgement. The goal is to bring together workforce data, opportunity requirements, availability, skills, project history, and recommendation reasoning so staffing decisions can be made faster and with more confidence.

---

## Tech Stack

### Frontend

- React
- TypeScript
- Vite
- React Router
- Axios

### Backend

- Spring Boot
- Java
- RESTful API architecture

### Database

- MongoDB

MongoDB stores workforce data, opportunity data, generated recommendations, validation data, analysis outputs, and EWA-related records.

---

## Project Structure

```txt
AI-Workforce-Planner/
  README.md

  frontend/
    src/
      api/
      assets/
      components/
        common/
        layout/
        talent/
        profile/
        opportunity/
        recommendation/
        ewa/
      data/
      pages/
      routes/
      types/
      utils/

  backend/
    src/
      main/
        java/
          com/
            aiworkforceplanner/
              config/
              controller/
              dto/
              model/
              repository/
              service/
        resources/
```

---

## Core Features

### 1. Workforce Dashboard

Shows a high-level workforce overview.

Planned information:

- Total employees
- Bench count
- Allocated count
- Upcoming availability
- Available people across 30, 60, and 90 days
- Availability by role, skill, grade, location, and region

The **New Opportunity** button navigates to the Opportunity Intake page.

---

### 2. Talent Explorer

Allows users to search and filter employees.

Main functions:

- Search employees
- Filter by skill, role, grade, location, domain, availability, and allocation status
- Display employee fit percentage based on filter results
- Click an employee row to view the Person Profile

When opened from Talent Explorer, the Person Profile shows only basic employee details.

---

### 3. Person Profile

The Person Profile has two display modes.

#### Basic Profile Mode

Opened from Talent Explorer.

Shows:

- Employee details
- Role
- Grade
- Location
- Skills
- Availability
- Current allocation
- Project history
- Domain experience

#### Recommendation Context Mode

Opened from the Recommendation page.

Shows everything in Basic Profile Mode, plus opportunity-specific recommendation notes:

- Why this person was recommended
- Matched skills
- Missing skills
- Availability fit
- Domain/project relevance
- Risks
- Suggested next actions

---

### 4. Opportunity Intake

Allows users to create a staffing opportunity using natural language and structured fields.

Example input:

```txt
Need 4 people for a banking modernization project in UK.
Skills: React, Java, AWS, QA Automation.
Start in 30 days.
```

Opportunity fields may include:

- Opportunity name
- Client/account
- Domain
- Project type
- Region/country
- Work mode
- Start date
- Duration
- Team size
- Required roles
- Required skills
- Optional skills
- Required grade/seniority
- Priority
- Notes

After the user generates options:

- Opportunity details are saved
- Generated recommendation options are saved
- User is navigated to the Recommendation page

---

### 5. Opportunity List

Stores and displays all created opportunities.

Each opportunity can contain:

- Opportunity details
- Required roles
- Required skills
- Generated recommendation options
- Selected option, when available
- Risk level
- Confidence score
- Status

The Opportunity List helps users reopen previous opportunities and review generated staffing options.

---

### 6. Recommendation Engine

The first implementation focuses on generating three basic team options.

Generated options:

1. **Best Skill Fit**
   - Prioritizes strongest skill and experience match.

2. **Fastest Available Team**
   - Prioritizes employees available now or soonest.

3. **Balanced Low-Risk Team**
   - Balances skills, availability, grade, location, and delivery risk.

The engine recommends suitable people based on:

- Skills
- Availability
- Grade
- Location
- Domain experience
- Relevant project experience

Candidate scoring logic:

```txt
Candidate Score =
Skills Match        35%
Availability Fit    25%
Domain Experience   15%
Grade Fit           10%
Location Fit        10%
Project Relevance    5%
```

---

### 7. Team Comparison

Compares generated staffing options side by side.

Comparison areas:

- Skill coverage
- Availability readiness
- Location fit
- Grade fit
- Risks
- Confidence score
- Missing capabilities

The first version should compare the three generated options only.

---

### 8. Selected / Custom Option

This feature is pending.

Planned future behavior:

- Show selected/custom option
- Allow user to select one generated option as a base
- Allow add/remove/replace employee
- Recalculate confidence score, skill coverage, availability readiness, risks, and missing skills

For now, complete the basic matching engine that produces the three generated options first.

---

### 9. Risk and Gap Analysis

Highlights risks and gaps based on dataset evidence.

Analysis includes:

- Missing skills
- People not available by required start date
- Grade mismatch
- Location mismatch
- Low confidence due to missing data
- Suggested next actions

Suggested actions may include:

- Reskilling
- Confirming availability
- Checking allocation release date
- Considering external sourcing
- Escalating to EWA for confirmation

---

### 10. EWA Review Pack

Generates the final recommendation summary for EWA review.

EWA remains the final approval and booking process.

```txt
The app recommends.
EWA approves and books.
```

The EWA Review Pack includes:

- Opportunity summary
- Selected team option
- Recommended people
- Reasoning
- Risks
- Gaps
- Suggested next actions

---

## Dataset Usage

The dataset contains both input data and expected/desired output data.

### Input Data

Used by the matching engine:

- Employee
- EmployeeSkill
- SkillCatalog
- Availability
- Allocation
- Bench
- Profile
- ProjectHistory
- Opportunity
- OpportunityRole

### Expected / Validation Data

Loaded into MongoDB first so other members can build analysis features without waiting for the matching engine.

- OpportunityOverlay
- EwaRequest

These records can be used later to validate or compare recommendation results.

Important rule:

```txt
OpportunityOverlay and EwaRequest should not be used as normal matching input.
They should be used for validation, analysis, comparison, and demo reporting.
```

---

## Main Development Priority

Current priority:

```txt
1. Load dataset into MongoDB
2. Build base frontend pages
3. Build Talent Explorer and Person Profile
4. Build Opportunity Intake and Opportunity List
5. Build basic matching engine
6. Generate three recommendation options
7. Build team comparison
8. Build risk and gap analysis
9. Build EWA Review Pack
10. Add selected/custom team option later
```

---

## Development Notes

This application should prioritize explainability.

Every recommendation should be supported by evidence such as:

- Matched skills
- Availability fit
- Domain experience
- Project history
- Grade fit
- Location fit
- Risks and gaps

The recommendation engine should be transparent and rule-based for the MVP. AI may assist with parsing and explanation, but the scoring logic should remain understandable and auditable.
