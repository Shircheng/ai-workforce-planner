# AI-Workforce-Planner

AI-Workforce-Planner is an AI-assisted workforce planning application built for the InSync Hackathon.

The application helps workforce planners, sales leaders, and delivery leaders explore available talent, create staffing opportunities, generate evidence-backed team recommendations, compare staffing options, and prepare final recommendations for EWA review.

The goal is not to replace human judgement. The goal is to bring together workforce data, opportunity requirements, availability, skills, project history, and recommendation reasoning so staffing decisions can be made faster and with more confidence.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Environment Variables](#environment-variables)
- [Docker Compose](#docker-compose)
- [Core Features](#core-features)
- [Dataset Usage](#dataset-usage)
- [API Endpoints](#api-endpoints)
- [Main Development Priority](#main-development-priority)
- [Development Notes](#development-notes)

---

## Tech Stack

### Frontend

- React
- TypeScript
- Vite
- React Router
- Zod for form validation
- Axios to fetch API for backend requests
- Component-level CSS for page and feature styling

### Backend

- Spring Boot
- Java
- Jakarta Bean Validation with `@Valid`
- Lombok DTO/entity helpers
- Jackson `ObjectMapper` for OpenAI JSON parsing
- RESTful API architecture
- OpenAI Responses API for opportunity requirement parsing, with a local fallback parser when OpenAI is unavailable

### Analytics

- Python analytics service
- MongoDB-driven insight APIs

The Python analytics service reads cleaned data from MongoDB and returns summarized analysis results for frontend visualization. 

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
      components/
        common/
        layout/
        talent/
        profile/
        opportunity/
        recommendation/
        ewa/
        analysis/
        opportunity/
      constants/
      hooks/
      pages/
      routes/
      types/
      utils/
      validation/

  backend/
    src/
      main/
        java/
          com/
            example/
              backend/
                config/
                controller/
                dto/
                entity/
                repository/
                service/
        resources/
```

---
## Environment Variables

### Frontend

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Spring Boot backend origin used by employee, import, opportunity, ewa, and recommendation APIs. Frontend calls paths such as `/api/opportunities/parse`. |
| `VITE_ANALYTICS_API_BASE` | `/python-analysis` | Python analytics API base path through the Vite proxy. |
| `VITE_BACKEND_PROXY_TARGET` | `http://127.0.0.1:8080` | Vite dev-server proxy target for `/api`. |
| `VITE_ANALYTICS_PROXY_TARGET` | `http://127.0.0.1:8000` | Vite dev-server proxy target for `/python-analysis`. |

### Backend

These are configured in `backend/src/main/resources/application.properties` and can be overridden with environment variables.

| Property / Variable | Default | Purpose |
| --- | --- | --- |
| `spring.mongodb.uri` / `SPRING_MONGODB_URI` | `mongodb://localhost:27017/ai-workforce-planner` | MongoDB database connection. |
| `app.import.employee-dataset` | `classpath:data/workforce-dataset.xlsx` | Default Excel dataset location. |
| `app.import.employee-dataset-on-startup` | `true` | Dataset startup import flag. |
| `app.cors.allowed-origins` | `http://localhost:5173` | Allowed frontend origin for backend API calls. |
| `openai.api-key` / `OPENAI_API_KEY` | empty when configured safely | OpenAI API key for opportunity parsing. Do not commit real keys. |
| `openai.model` / `OPENAI_MODEL` | project configured model | OpenAI model used by opportunity parsing. |

Note: Dataset import is explicit. Employee GET APIs do not auto-import data.

### Python Analytics

| Variable | Default | Purpose |
| --- | --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017` | MongoDB server URI |
| `MONGODB_DATABASE` | `ai-workforce-planner` | MongoDB database name |

---

## Docker Compose

Docker Compose can start MongoDB, Spring Boot, Python analytics, and the React frontend together.

```powershell
docker compose up --build
```

Service ports:

| Service | URL |
| --- | --- |
| Frontend | `http://localhost:5173` |
| Spring Boot backend | `http://localhost:8080` |
| Python analytics | `http://127.0.0.1:8000` |
| MongoDB | `mongodb://localhost:27017` |

After the containers are running, import a dataset from the Dashboard by choosing the Excel file. The frontend uploads it to:

```txt
POST http://localhost:8080/api/import/workforce-dataset/upload
```

You can also import the default backend resource directly:

```txt
POST http://localhost:8080/api/import/workforce-dataset
```

Docker-specific connection values are already configured in `docker-compose.yml`:

- Backend uses `mongodb://mongodb:27017/ai-workforce-planner`
- Python analytics uses `mongodb://mongodb:27017`
- Frontend proxies `/api` to `http://backend:8080`
- Frontend proxies `/python-analysis` to `http://python-analytics:8000`

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

The current intake flow is:

1. User fills required business fields and writes an opportunity statement.
2. User clicks **Parse requirements**.
3. Frontend validates required fields with Zod before calling the backend.
4. Backend parses or infers opportunity roles and role requirements using OpenAI when available. A local fallback parser provides basic extraction for roles, known skills, grade preferences, FTE, split-candidate wording, domain, location preference, and selected flexibility notes.
5. User reviews the validated preview and role cards.
6. User clicks **Generate options**.
7. The opportunity and opportunity roles are saved to MongoDB, then the app navigates to the Recommendation page.

The opportunity is **not saved** during parsing. It is saved only after the user confirms **Generate options**.

Required intake fields:

- Opportunity statement
- Opportunity brief
- Opportunity name
- Client name
- Domain
- Country
- Expected start date
- Duration weeks
- Probability
- Commercial priority

Optional intake fields:

- Client type
- Region
- City
- Timezone preference

Current parsing and validation behavior:

- Form fields are authoritative when they conflict with statement text.
- Opportunity IDs and Opportunity role IDs are generated from the latest MongoDB opportunity/ opportunity role number, such as `OPP-005`, `OPR-0061`.
- Role generation is blocked when role name, grade preference, required skills, or desired skills are incomplete.
- Delivery risk is inferred by OpenAI when available and defaults to `Medium` when missing.
- Role priority is normalized to `High`, `Medium`, or `Low`, falling back to commercial priority and then `Medium`.

---

### 5. Recommendation Engine

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

### 6. Team Comparison

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

### 7. Skill Gap and Workforce Forecast Analysis

Shows workforce insights based on data.

Skill gap analysis includes:

- Required skill coverage
- Priority skill gap
- Average fit percentage
- Employees evaluated after filters
- Ready candidates who match all required skills
- Grouped insight by region, country, city, role, grade, discipline, domain, availability category, or work mode

Workforce forecast includes:

- Available people across 30, 60, and 90 days
- Available FTE across 30, 60, and 90 days
- Forecast grouped by workforce dimensions such as region, role, grade, location, and domain

Opportunity forecast includes:

- Total required FTE from open opportunities
- Probability-weighted forecast FTE
- Expected workload in FTE-weeks
- Demand forecast by time window
- Skill demand forecast

The analysis pages are insight-only. They should not expose detailed employee profile information unless the user navigates to a dedicated employee view.

---

### 8. EWA Review Pack

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

## API Endpoints

### Python Analytics Service

Base URL:

```txt
http://127.0.0.1:8000
```

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/health` | Check whether the Python analytics service is running |
| GET | `/analysis/filter-options` | Return dynamic filter dropdown values from MongoDB distinct values |
| POST | `/analysis/skill-gap` | Analyze required skill coverage, fit percentage, priority gaps, and ready candidates |
| POST | `/analysis/workforce-forecast` | Analyze workforce availability across 30, 60, and 90 days |
| POST | `/analysis/opportunity-forecast` | Analyze opportunity demand, probability-weighted FTE, workload, and skill demand |
| POST | `/analysis/ewa-summary` | Summarize EWA request status and booking evidence |

### Spring Boot Backend

Base URL:

```txt
http://localhost:8080/api
```

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/import/workforce-dataset` | Import the default workforce dataset from backend resources into MongoDB |
| POST | `/import/workforce-dataset/upload` | Upload and import a workforce Excel file |
| GET | `/employees` | Search and filter employees |
| GET | `/employees/{employeeId}` | Fetch one employee by employee ID |
| GET | `/employees/filter-options` | Return filter options for Talent Explorer |
| GET | `/employees/{employeeId}/profile` | Fetch employee profile details |
| POST | `/opportunities/parse` | Parse and validate an opportunity intake request. This returns a preview and does not save the opportunity. |
| POST | `/opportunities/generate-options` | Save the parsed opportunity and opportunity roles, then provide recommendation. |
| GET | `/opportunities/{opportunityId}` | Fetch one opportunity by opportunity ID |
| GET | `/opportunity-roles/{opportunityRoleId}` | Fetch one opportunity role by role ID |
| GET | `/opportunity-overlays/{overlayId}` | Fetch one opportunity overlay by overlay ID |
| POST | `/ewa-requests/submit` | Create new EWA request records for selected candidates |

Dataset import is only triggered through the import endpoints. Employee GET endpoints return the data currently stored in MongoDB.

Frontend development may call Spring Boot through the Vite `/api` proxy. The analysis page calls the Python analytics service for insight and visualization data.

---

## Main Development Priority

Current priority:

```txt
1. Load dataset into MongoDB
2. Build base frontend pages
3. Build Talent Explorer and Person Profile
4. Build Opportunity Intake and Requirement Parsing
6. Build recommendation option generation
7. Build team comparison
8. Build risk and gap analysis
9. Build EWA Review Pack
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
