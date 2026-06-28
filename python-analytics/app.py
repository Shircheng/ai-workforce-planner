import json
import os
from collections import Counter, defaultdict
from datetime import date, datetime, timedelta
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

from bson.decimal128 import Decimal128
from pymongo import MongoClient


MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
MONGODB_DATABASE = os.getenv("MONGODB_DATABASE", "ai-workforce-planner")
HOST = os.getenv("PYTHON_ANALYTICS_HOST", "127.0.0.1")
PORT = int(os.getenv("PYTHON_ANALYTICS_PORT", "8000"))

client = MongoClient(MONGODB_URI)
db = client[MONGODB_DATABASE]


def to_float(value, default=0.0):
    if value is None:
        return default
    if isinstance(value, Decimal128):
        return float(value.to_decimal())
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except (TypeError, ValueError):
        return default


def to_int(value, default=0):
    try:
        return int(to_float(value, default))
    except (TypeError, ValueError):
        return default


def to_date(value):
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    if isinstance(value, str):
        try:
            return date.fromisoformat(value[:10])
        except ValueError:
            return None
    return None


def text(value):
    if value is None:
        return ""
    return str(value).strip()


def key(value):
    return text(value).lower()


def split_list(value):
    if isinstance(value, list):
        return [text(item) for item in value if text(item)]
    raw = text(value)
    if not raw:
        return []
    separators = [";", "|", "\n"]
    for separator in separators:
        raw = raw.replace(separator, ",")
    return [item.strip() for item in raw.split(",") if item.strip()]


def request_filters(body):
    filters = dict(body.get("filters") or {})
    for field in [
        "region",
        "country",
        "city",
        "location",
        "grade",
        "role",
        "roleArchetype",
        "discipline",
        "domain",
        "availabilityCategory",
        "workMode",
    ]:
        if body.get(field) is not None:
            filters[field] = body.get(field)
    return filters


def matches_filter(actual, expected):
    if expected in (None, "", []):
        return True
    if isinstance(expected, list):
        return key(actual) in {key(item) for item in expected}
    return key(actual) == key(expected)


def employee_matches(employee, filters):
    if not filters:
        return True

    if not matches_filter(employee.get("region"), filters.get("region")):
        return False
    if not matches_filter(employee.get("country"), filters.get("country")):
        return False
    if not matches_filter(employee.get("city"), filters.get("city")):
        return False
    if not matches_filter(employee.get("grade"), filters.get("grade")):
        return False
    if not matches_filter(employee.get("discipline"), filters.get("discipline")):
        return False
    if not matches_filter(employee.get("availabilityCategory"), filters.get("availabilityCategory")):
        return False
    if not matches_filter(employee.get("workMode"), filters.get("workMode")):
        return False

    role_filter = filters.get("role") or filters.get("roleArchetype")
    if not matches_filter(employee.get("roleArchetype"), role_filter):
        return False

    location_filter = filters.get("location")
    if location_filter:
        location_values = [
            employee.get("city"),
            employee.get("country"),
            employee.get("region"),
        ]
        if key(location_filter) not in {key(value) for value in location_values}:
            return False

    domain_filter = filters.get("domain")
    if domain_filter:
        domain_values = [
            employee.get("primaryDomain"),
            employee.get("secondaryDomain"),
        ]
        if key(domain_filter) not in {key(value) for value in domain_values}:
            return False

    return True


def filtered_employees(filters):
    employees = list(db.employees.find({}))
    return [employee for employee in employees if employee_matches(employee, filters)]


def group_value(employee, group_by):
    if not group_by:
        return None
    mapping = {
        "role": "roleArchetype",
        "location": "city",
        "domain": "primaryDomain",
    }
    return text(employee.get(mapping.get(group_by, group_by))) or "Unknown"


def sorted_counts(counter):
    return [
        {"label": label, "count": count}
        for label, count in sorted(counter.items(), key=lambda item: item[0])
    ]


def distinct_values(collection_name, field_name):
    values = set()
    for value in db[collection_name].distinct(field_name):
        if isinstance(value, list):
            values.update(text(item) for item in value if text(item))
        elif text(value):
            values.add(text(value))
    return sorted(values)


def filter_options():
    return {
        "employeeGroupByOptions": [
            "region",
            "country",
            "city",
            "location",
            "grade",
            "role",
            "discipline",
            "domain",
            "availabilityCategory",
            "workMode",
        ],
        "filters": {
            "regions": distinct_values("employees", "region"),
            "countries": distinct_values("employees", "country"),
            "cities": distinct_values("employees", "city"),
            "grades": distinct_values("employees", "grade"),
            "roles": distinct_values("employees", "roleArchetype"),
            "disciplines": distinct_values("employees", "discipline"),
            "domains": sorted(
                set(distinct_values("employees", "primaryDomain"))
                | set(distinct_values("employees", "secondaryDomain"))
            ),
            "availabilityCategories": distinct_values("employees", "availabilityCategory"),
            "workModes": distinct_values("employees", "workMode"),
            "skillCategories": sorted(
                set(distinct_values("skill_catalog", "skillCategory"))
                | set(distinct_values("employee_skills", "skillCategory"))
            ),
        },
    }


def build_skill_index(employee_ids):
    query = {"employeeId": {"$in": list(employee_ids)}} if employee_ids else {}
    skills = list(db.employee_skills.find(query))
    by_employee = defaultdict(list)
    by_skill = defaultdict(set)
    for skill in skills:
        employee_id = skill.get("employeeId")
        skill_name = key(skill.get("skillName"))
        by_employee[employee_id].append(skill)
        by_skill[skill_name].add(employee_id)
    return skills, by_employee, by_skill


def build_skill_category_index(required_skills, employee_skills):
    categories = {}
    for item in db.skill_catalog.find({}):
        skill_name = key(item.get("skillName"))
        skill_category = text(item.get("skillCategory"))
        if skill_name and skill_category:
            categories[skill_name] = skill_category

    for skill in employee_skills:
        skill_name = key(skill.get("skillName"))
        skill_category = text(skill.get("skillCategory"))
        if skill_name and skill_category and skill_name not in categories:
            categories[skill_name] = skill_category

    return {
        key(skill_name): categories.get(key(skill_name), "Uncategorized")
        for skill_name in required_skills
    }


def analyze_skill_gap(body):
    required_skills = [text(skill) for skill in body.get("requiredSkills", []) if text(skill)]
    min_skill_level = to_int(body.get("minSkillLevel"), 3)
    group_by = body.get("groupBy")
    filters = request_filters(body)
    skill_category_filter = text((body.get("filters") or {}).get("skillCategory") or body.get("skillCategory"))

    employees = filtered_employees(filters)
    employee_ids = {employee.get("employeeId") for employee in employees if employee.get("employeeId")}
    skills, by_employee, _ = build_skill_index(employee_ids)

    total_employees = len(employees)
    required_keys = [key(skill) for skill in required_skills]
    category_by_skill = build_skill_category_index(required_skills, skills)
    if skill_category_filter:
        required_pairs = [
            (display_name, skill_key)
            for display_name, skill_key in zip(required_skills, required_keys)
            if matches_filter(category_by_skill.get(skill_key), skill_category_filter)
        ]
        required_skills = [display_name for display_name, _ in required_pairs]
        required_keys = [skill_key for _, skill_key in required_pairs]
    matched_by_skill = {skill: set() for skill in required_keys}
    matched_count_by_employee = Counter()

    for skill in skills:
        skill_name = key(skill.get("skillName"))
        employee_id = skill.get("employeeId")
        if skill_name in matched_by_skill and to_int(skill.get("skillLevel")) >= min_skill_level:
            matched_by_skill[skill_name].add(employee_id)
            matched_count_by_employee[employee_id] += 1

    skill_gaps = []
    for display_name, skill_key in zip(required_skills, required_keys):
        matched_count = len(matched_by_skill[skill_key])
        missing_count = max(total_employees - matched_count, 0)
        coverage = round((matched_count / total_employees) * 100, 2) if total_employees else 0.0
        skill_gaps.append(
            {
                "skillName": display_name,
                "skillCategory": category_by_skill.get(skill_key, "Uncategorized"),
                "matchedEmployeeCount": matched_count,
                "missingEmployeeCount": missing_count,
                "coveragePercentage": coverage,
            }
        )

    required_count = len(required_skills)
    if required_count and total_employees:
        average_fit = sum(
            matched_count_by_employee[employee.get("employeeId")] / required_count * 100
            for employee in employees
        ) / total_employees
    else:
        average_fit = 0.0
    ready_candidate_count = sum(
        1
        for employee in employees
        if required_count
        and matched_count_by_employee[employee.get("employeeId")] >= required_count
    )

    grouped = []
    grouped_skill_coverage = []
    if group_by:
        employees_by_group = defaultdict(list)
        for employee in employees:
            employees_by_group[group_value(employee, group_by)].append(employee)

        for value, group_employees in employees_by_group.items():
            group_size = len(group_employees)
            group_fit_total = 0.0
            total_missing = 0
            for employee in group_employees:
                employee_id = employee.get("employeeId")
                matched = matched_count_by_employee[employee_id]
                total_missing += max(required_count - matched, 0)
                group_fit_total += (matched / required_count * 100) if required_count else 0
            grouped.append(
                {
                    "groupBy": group_by,
                    "groupValue": value,
                    "employeeCount": group_size,
                    "totalMissingSkillCount": total_missing,
                    "averageFitPercentage": round(group_fit_total / group_size, 2) if group_size else 0.0,
                }
            )
            group_employee_ids = {employee.get("employeeId") for employee in group_employees}
            for display_name, skill_key in zip(required_skills, required_keys):
                matched_count = len(matched_by_skill[skill_key] & group_employee_ids)
                grouped_skill_coverage.append(
                    {
                        "groupBy": group_by,
                        "groupValue": value,
                        "skillName": display_name,
                        "skillCategory": category_by_skill.get(skill_key, "Uncategorized"),
                        "matchedEmployeeCount": matched_count,
                        "missingEmployeeCount": max(group_size - matched_count, 0),
                        "coveragePercentage": round((matched_count / group_size) * 100, 2)
                        if group_size
                        else 0.0,
                    }
                )

    return {
        "totalEmployeesEvaluated": total_employees,
        "totalRequiredSkillCount": required_count,
        "averageFitPercentage": round(average_fit, 2),
        "readyCandidateCount": ready_candidate_count,
        "skillGaps": skill_gaps,
        "groupedInsights": sorted(grouped, key=lambda item: item["groupValue"]),
        "groupedSkillCoverage": sorted(
            grouped_skill_coverage,
            key=lambda item: (item["groupValue"], item["skillName"]),
        ),
    }


def forecast_available_fte(employee, cutoff):
    current_available = to_float(employee.get("availableFTECurrent"))
    release_date = to_date(employee.get("expectedReleaseDate"))

    if current_available > 0:
        return current_available

    if release_date and release_date <= cutoff:
        return 1.0

    return 0.0


def forecast_available_employee_rows(employees, cutoff):
    available = []
    total_fte = 0.0

    for employee in employees:
        employee_fte = forecast_available_fte(employee, cutoff)
        if employee_fte > 0:
            available.append((employee, employee_fte))
            total_fte += employee_fte

    return available, total_fte


def analyze_workforce_forecast(body):
    filters = request_filters(body)
    as_of = to_date(body.get("asOfDate")) or date.today()
    horizons = body.get("horizons") or [30, 60, 90]
    group_by = body.get("groupBy")
    employees = filtered_employees(filters)

    forecast = []
    for horizon in horizons:
        horizon = to_int(horizon)
        cutoff = as_of + timedelta(days=horizon)
        available_rows, total_fte = forecast_available_employee_rows(employees, cutoff)

        bucket = {
            "horizonDays": horizon,
            "asOfDate": as_of.isoformat(),
            "cutoffDate": cutoff.isoformat(),
            "availableEmployeeCount": len(available_rows),
            "totalAvailableFte": round(total_fte, 2),
        }

        if group_by:
            group_employees = defaultdict(list)
            for employee, employee_fte in available_rows:
                group_employees[group_value(employee, group_by)].append((employee, employee_fte))
            bucket["groupedAvailability"] = []
            for value, group in sorted(group_employees.items()):
                bucket["groupedAvailability"].append(
                    {
                        "groupBy": group_by,
                        "groupValue": value,
                        "availableEmployeeCount": len(group),
                        "totalAvailableFte": round(
                            sum(employee_fte for _, employee_fte in group),
                            2,
                        ),
                    }
                )

        forecast.append(bucket)

    return {
        "totalEmployeesEvaluated": len(employees),
        "forecast": forecast,
    }


def opportunity_probability(opportunity):
    probability = to_float(opportunity.get("probability"), 1.0)
    if probability > 1:
        probability = probability / 100
    return min(max(probability, 0.0), 1.0)


def opportunity_start_date(opportunity, role):
    return to_date(role.get("startDate")) or to_date(opportunity.get("expectedStartDate"))


def opportunity_filter_matches(opportunity, role, filters):
    if not matches_filter(opportunity.get("region"), filters.get("region")):
        return False
    if not matches_filter(opportunity.get("country"), filters.get("country")):
        return False
    if not matches_filter(opportunity.get("city"), filters.get("city")):
        return False
    if not matches_filter(opportunity.get("domain"), filters.get("domain")):
        return False
    if not matches_filter(opportunity.get("stage"), filters.get("stage")):
        return False
    if not matches_filter(opportunity.get("commercialPriority"), filters.get("commercialPriority")):
        return False
    if not matches_filter(opportunity.get("deliveryRisk"), filters.get("deliveryRisk")):
        return False
    if not matches_filter(role.get("roleName"), filters.get("role") or filters.get("roleArchetype")):
        return False
    if not matches_filter(role.get("gradePreference"), filters.get("grade")):
        return False
    if not matches_filter(role.get("disciplineOrDepartment"), filters.get("discipline")):
        return False

    return True


def opportunity_role_rows(filters):
    opportunities = {
        opportunity.get("opportunityId"): opportunity
        for opportunity in db.opportunities.find({})
        if opportunity.get("opportunityId")
    }
    rows = []

    for role in db.opportunity_roles.find({}):
        opportunity = opportunities.get(role.get("opportunityId"))
        if not opportunity or not opportunity_filter_matches(opportunity, role, filters):
            continue

        start_date = opportunity_start_date(opportunity, role)
        if start_date:
            rows.append((opportunity, role, start_date))

    return rows


def opportunity_skill_names(role):
    return split_list(role.get("requiredSkills")) or split_list(role.get("desiredSkills"))


def summarize_opportunity_window(rows, window_start, window_end):
    window_rows = [
        (opportunity, role, start_date)
        for opportunity, role, start_date in rows
        if window_start <= start_date <= window_end
    ]
    skill_demand = Counter()
    required_fte = 0.0
    forecast_fte = 0.0

    for opportunity, role, _ in window_rows:
        role_fte = to_float(role.get("fteRequired"), 1.0)
        weighted_fte = role_fte * opportunity_probability(opportunity)
        required_fte += role_fte
        forecast_fte += weighted_fte

        for skill_name in opportunity_skill_names(role):
            skill_demand[text(skill_name)] += weighted_fte

    return {
        "requiredFte": round(required_fte, 2),
        "forecastFte": round(forecast_fte, 2),
        "mainSkills": [
            skill_name
            for skill_name, _ in skill_demand.most_common(3)
        ],
    }


def analyze_opportunity_forecast(body):
    filters = dict(body.get("filters") or {})
    as_of = to_date(body.get("asOfDate")) or date.today()
    horizons = sorted({to_int(horizon) for horizon in (body.get("horizons") or [30, 60, 90]) if to_int(horizon) > 0})
    max_horizon = max(horizons) if horizons else 90
    final_cutoff = as_of + timedelta(days=max_horizon)
    rows = [
        (opportunity, role, start_date)
        for opportunity, role, start_date in opportunity_role_rows(filters)
        if as_of <= start_date <= final_cutoff
    ]
    opportunity_ids = {opportunity.get("opportunityId") for opportunity, _, _ in rows}

    total_required_fte = 0.0
    probability_forecast_fte = 0.0
    expected_workload = 0.0
    high_risk_demand = 0.0
    high_priority_ids = set()
    skill_required = Counter()
    skill_forecast = Counter()
    skill_opportunities = defaultdict(set)
    opportunity_summary = {}

    for opportunity, role, start_date in rows:
        opportunity_id = opportunity.get("opportunityId")
        role_fte = to_float(role.get("fteRequired"), 1.0)
        probability = opportunity_probability(opportunity)
        weighted_fte = role_fte * probability
        duration_weeks = to_int(role.get("durationWeeks") or opportunity.get("durationWeeks"), 0)

        total_required_fte += role_fte
        probability_forecast_fte += weighted_fte
        expected_workload += weighted_fte * duration_weeks

        if key(opportunity.get("commercialPriority")) == "high":
            high_priority_ids.add(opportunity_id)
        if key(opportunity.get("deliveryRisk")) == "high":
            high_risk_demand += weighted_fte

        summary = opportunity_summary.setdefault(
            opportunity_id,
            {
                "opportunityName": text(opportunity.get("opportunityName")) or opportunity_id,
                "startDate": start_date,
                "probability": probability,
                "requiredFte": 0.0,
                "forecastFte": 0.0,
                "deliveryRisk": text(opportunity.get("deliveryRisk")) or "Unknown",
                "skills": Counter(),
            },
        )
        summary["startDate"] = min(summary["startDate"], start_date)
        summary["requiredFte"] += role_fte
        summary["forecastFte"] += weighted_fte

        for skill_name in opportunity_skill_names(role):
            normalized_skill = text(skill_name)
            if not normalized_skill:
                continue
            skill_required[normalized_skill] += role_fte
            skill_forecast[normalized_skill] += weighted_fte
            skill_opportunities[normalized_skill].add(opportunity_id)
            summary["skills"][normalized_skill] += weighted_fte

    top_skill_name, top_skill_value = ("-", 0.0)
    if skill_forecast:
        top_skill_name, top_skill_value = skill_forecast.most_common(1)[0]

    previous_day_offset = 0
    demand_windows = []
    for index, horizon in enumerate(horizons):
        window_start_offset = previous_day_offset if index == 0 else previous_day_offset + 1
        window_start = as_of + timedelta(days=window_start_offset)
        window_end = as_of + timedelta(days=horizon)
        label = f"{previous_day_offset}-{horizon} days" if index == 0 else f"{previous_day_offset + 1}-{horizon} days"
        window = summarize_opportunity_window(rows, window_start, window_end)
        demand_windows.append(
            {
                "window": label,
                "requiredFte": window["requiredFte"],
                "forecastFte": window["forecastFte"],
                "mainSkills": window["mainSkills"],
            }
        )
        previous_day_offset = horizon

    skill_demand = [
        {
            "skillName": skill_name,
            "requiredFte": round(skill_required[skill_name], 2),
            "forecastFte": round(skill_forecast[skill_name], 2),
            "opportunityCount": len(skill_opportunities[skill_name]),
        }
        for skill_name, _ in skill_forecast.most_common(10)
    ]

    opportunity_table = []
    for summary in sorted(opportunity_summary.values(), key=lambda item: item["startDate"])[:20]:
        opportunity_table.append(
            {
                "opportunityName": summary["opportunityName"],
                "startDate": summary["startDate"].isoformat(),
                "probability": round(summary["probability"] * 100, 0),
                "requiredFte": round(summary["requiredFte"], 2),
                "forecastFte": round(summary["forecastFte"], 2),
                "deliveryRisk": summary["deliveryRisk"],
                "skills": [
                    skill_name
                    for skill_name, _ in summary["skills"].most_common(4)
                ],
            }
        )

    return {
        "asOfDate": as_of.isoformat(),
        "summary": {
            "totalOpportunities": len(opportunity_ids),
            "totalRequiredFte": round(total_required_fte, 2),
            "probabilityForecastFte": round(probability_forecast_fte, 2),
            "expectedWorkloadFteWeeks": round(expected_workload, 2),
            "forecastWindowDays": max_horizon,
            "forecastWindowWeeks": round(max_horizon / 7, 1),
            "highPriorityOpportunities": len(high_priority_ids),
            "highRiskDemandFte": round(high_risk_demand, 2),
            "topSkillName": top_skill_name,
            "topSkillForecastFte": round(top_skill_value, 2),
        },
        "demandWindows": demand_windows,
        "skillDemand": skill_demand,
        "opportunities": opportunity_table,
    }


def analyze_ewa_summary(body):
    query = {}
    if body.get("opportunityId"):
        query["opportunityId"] = body["opportunityId"]
    if body.get("opportunityRoleId"):
        query["opportunityRoleId"] = body["opportunityRoleId"]

    requests = list(db.ewa_requests.find(query))
    statuses = Counter(text(item.get("ewaStatus") or "Unknown") for item in requests)
    approval_required = Counter(text(item.get("approvalRequired") or "Unknown") for item in requests)
    can_split = Counter(text(item.get("canSplitRole") or "Unknown") for item in requests)
    blocking_reasons = Counter(
        text(item.get("blockingReason"))
        for item in requests
        if key(item.get("blockingReason")) not in {"", "none"}
    )

    return {
        "opportunityId": body.get("opportunityId"),
        "opportunityRoleId": body.get("opportunityRoleId"),
        "totalEwaRequests": len(requests),
        "statusCounts": dict(statuses),
        "approvalRequiredCounts": dict(approval_required),
        "canSplitRoleCounts": dict(can_split),
        "totalRequestedFte": round(sum(to_float(item.get("requestedFTE")) for item in requests), 2),
        "totalFteGap": round(sum(to_float(item.get("fteGap")) for item in requests), 2),
        "blockingReasonCounts": dict(blocking_reasons),
    }


ROUTES = {
    "/analysis/skill-gap": analyze_skill_gap,
    "/analysis/skill-gaps": analyze_skill_gap,
    "/analysis/workforce-forecast": analyze_workforce_forecast,
    "/analysis/opportunity-forecast": analyze_opportunity_forecast,
    "/analysis/ewa-summary": analyze_ewa_summary,
}


class AnalyticsHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            self.send_json({"status": "UP", "database": MONGODB_DATABASE})
            return
        if path == "/analysis/filter-options":
            self.send_json(filter_options())
            return
        self.send_json({"error": "Not found"}, status=404)

    def do_POST(self):
        path = urlparse(self.path).path
        handler = ROUTES.get(path)
        if handler is None:
            self.send_json({"error": "Not found"}, status=404)
            return

        try:
            body = self.read_json()
            result = handler(body)
            self.send_json(result)
        except Exception as exc:
            self.send_json({"error": str(exc)}, status=500)

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length == 0:
            return {}
        payload = self.rfile.read(length).decode("utf-8")
        return json.loads(payload) if payload else {}

    def send_json(self, payload, status=200):
        response = json.dumps(payload, default=str).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, format, *args):
        return


def main():
    server = ThreadingHTTPServer((HOST, PORT), AnalyticsHandler)
    print(f"Python analytics running at http://{HOST}:{PORT}")
    print(f"Reading MongoDB database: {MONGODB_DATABASE}")
    server.serve_forever()


if __name__ == "__main__":
    main()
