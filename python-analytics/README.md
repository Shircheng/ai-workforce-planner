# Python Analytics Service

`app.py` is a small Python HTTP service for insight-only workforce analytics. It reads cleaned workforce data directly from MongoDB and returns JSON that the React analysis page can visualize.

It does not import Excel data. Excel import and data cleaning should happen in the Spring Boot backend first, then the cleaned documents are stored in MongoDB.

## What It Analyzes

The service currently supports:

- Skill gap analysis
- Workforce availability forecast
- Opportunity demand forecast
- EWA request summary
- Filter option lookup for frontend dropdowns

## Data Source

Default MongoDB connection:

```text
URI: mongodb://localhost:27017
Database: ai-workforce-planner
```

Override with environment variables if needed:

```powershell
$env:MONGODB_URI="mongodb://localhost:27017"
$env:MONGODB_DATABASE="ai-workforce-planner"
```

## MongoDB Collections Used

| Collection | Used For |
| --- | --- |
| `employees` | Employee profile, role, grade, location, domain, availability, FTE |
| `employee_skills` | Employee skill names, skill levels, skill categories |
| `skill_catalog` | Skill category lookup |
| `opportunities` | Opportunity probability, priority, risk, dates, domain, location |
| `opportunity_roles` | Role-level demand, required FTE, skills, duration, start date |
| `ewa_requests` | EWA approval, booking, split-role, blocking, FTE gap summary |

## Setup

From the project root:

```powershell
cd C:\Users\qfoong\ai-workforce-planner\python-analytics
py -m pip install -r requirements.txt
```

Run the service:

```powershell
py app.py
```

Expected console output:

```text
Python analytics running at http://127.0.0.1:8000
Reading MongoDB database: ai-workforce-planner
```

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

## Frontend Connection

The Vite frontend proxies Python analytics requests through:

```text
/python-analysis -> http://127.0.0.1:8000
```

So the frontend can call:

```text
/python-analysis/analysis/skill-gap
```

instead of calling port `8000` directly.

## Endpoints

### GET `/health`

Checks whether the Python service is running.

### GET `/analysis/filter-options`

Returns dynamic dropdown values for filters such as region, role, grade, domain, availability, work mode, and skill category.

### POST `/analysis/skill-gap`

Shows how well the filtered workforce covers required skills.

Example body:

```json
{
  "requiredSkills": ["React", "Java", "AWS"],
  "minSkillLevel": 3,
  "groupBy": "region",
  "filters": {
    "region": "APAC",
    "skillCategory": "Engineering"
  }
}
```

Main output:

- `totalEmployeesEvaluated`: employees included after filters
- `averageFitPercentage`: average skill fit across evaluated employees
- `readyCandidateCount`: employees matching all required skills
- `skillGaps`: coverage per required skill
- `groupedInsights`: summary by selected group
- `groupedSkillCoverage`: skill coverage by group for charts

### POST `/analysis/workforce-forecast`

Forecasts employee availability across future windows.

Example body:

```json
{
  "asOfDate": "2026-06-28",
  "horizons": [30, 60, 90],
  "groupBy": "region",
  "filters": {
    "domain": "Banking"
  }
}
```

Main output:

- available people by horizon
- available FTE by horizon
- optional grouped availability by region, role, location, grade, domain, and more

### POST `/analysis/opportunity-forecast`

Forecasts demand from all opportunity roles within the selected time windows. Demand is weighted by opportunity probability.

Example body:

```json
{
  "asOfDate": "2026-06-28",
  "horizons": [30, 60, 90],
  "filters": {
    "region": "APAC"
  }
}
```

Main output:

- `totalRequiredFte`: full demand if all opportunities happen
- `probabilityForecastFte`: demand weighted by probability
- `expectedWorkloadFteWeeks`: forecast FTE multiplied by role duration
- `demandWindows`: demand by time window
- `skillDemand`: top skills required by opportunity demand

### POST `/analysis/ewa-summary`

Summarizes EWA requests. This is useful for approval or booking insight, not skill matching.

Example body:

```json
{
  "opportunityId": "OPP-001"
}
```

Main output:

- EWA status counts
- approval required counts
- split-role counts
- requested FTE total
- FTE gap total
- blocking reason counts

## Postman Testing

1. Start MongoDB.
2. Make sure cleaned data exists in MongoDB.
3. Start the Python service with `py app.py`.
4. In Postman, use:

```text
POST http://127.0.0.1:8000/analysis/skill-gap
Content-Type: application/json
```

Body:

```json
{
  "requiredSkills": ["React", "Java", "AWS"],
  "minSkillLevel": 3,
  "groupBy": "region"
}
```

## Notes

- The service returns aggregated insight data only. It does not return detailed employee profiles.
- Skill matching uses `employee_skills.skillName` and `employee_skills.skillLevel`.
- Workforce forecast uses `employees.availableFTECurrent` and `employees.expectedReleaseDate`.
- Opportunity forecast uses `opportunities.probability` and `opportunity_roles.fteRequired`.
- Restart `app.py` after changing Python code.
