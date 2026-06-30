import axios, { AxiosError } from 'axios'
import type { Employee } from '../types/Employee'
import type { Opportunity } from '../types/Opportunity'
import type { OpportunityOverlay } from '../types/OpportunityOverlays'
import type { OpportunityRole } from '../types/OpportunityRoles'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

export type EwaOpportunitySummary = {
  opportunityName?: string
  clientName?: string
  domain?: string
  region?: string
  country?: string
  city?: string
  expectedStartDate?: string
  durationWeeks?: number
  probability?: number
  commercialPriority?: string
  deliveryRisk?: string
  opportunityBrief?: string
}

export type EwaTeamOptionSummary = {
  optionName?: string
  matchScore?: number
  confidence?: number
  riskLevel?: string
  teamSize?: number
  locationFitScore?: number
  locationFit?: string[]
  skillCoverageScore?: number
  matchedRequiredSkills?: string[]
  missingRequiredSkills?: string[]
  matchedDesiredSkills?: string[]
  missingDesiredSkills?: string[]
}

export type EwaRequestSnapshot = {
  ewaRequestId?: string
  opportunityRoleId?: string
  employeeId?: string
  ewaStatus?: string
}

export type EwaRecommendedPerson = {
  employeeId?: string
  employeeName?: string
  opportunityRoleId?: string
  role?: string
  matchScore?: number
  capabilityFitScore?: number
  availabilityFitScore?: number
  overallStaffingScore?: number
  availableFTEAtStart?: number
  fteGap?: number
  requiredSkillsMatched?: number
  requiredSkillsTotal?: number
  desiredSkillsMatched?: number
  desiredSkillsTotal?: number
}

export type EwaReviewPack = {
  opportunityId: string
  status: string
  generatedAt?: string
  opportunitySummary?: EwaOpportunitySummary
  selectedTeamOption?: EwaTeamOptionSummary
  recommendedPeople?: EwaRecommendedPerson[]
}

export type EwaRequestSubmitRequest = {
  opportunityId: string
  opportunityRoleId: string
  employeeId: string
  availableFTEAtStart?: number
  fteGap?: number
  notes?: string
}

export type EwaSelectedCandidateReference = {
  opportunityOverlayId?: string
  overlayId?: string
  opportunityId?: string
  opportunityRoleId: string
  employeeId: string
  matchScore?: number
  capabilityFitScore?: number
  availabilityFitScore?: number
  overallStaffingScore?: number
  availableFTEAtStart?: number
  fteGap?: number
  locationFitScore?: number
  locationFit?: string[]
  skillCoverageScore?: number
  requiredSkillsMatched?: number
  requiredSkillsTotal?: number
  desiredSkillsMatched?: number
  desiredSkillsTotal?: number
  matchedRequiredSkills?: string[]
  missingRequiredSkills?: string[]
  matchedDesiredSkills?: string[]
  missingDesiredSkills?: string[]
}

export type EwaRecommendationSelectionPayload = {
  opportunityId: string
  selectedOption?: EwaTeamOptionSummary
  selectedCandidates: EwaSelectedCandidateReference[]
}

export type EwaHydratedCandidate = EwaSelectedCandidateReference & {
  employee?: Employee
  role?: OpportunityRole
  overlay?: OpportunityOverlay
}

export type EwaHydratedSelectionData = {
  opportunity: Opportunity
  selectedOption?: EwaTeamOptionSummary
  selectedCandidates: EwaHydratedCandidate[]
}

async function requestJson<T>(path: string): Promise<T> {
  try {
    const response = await apiClient.get<T>(path)
    return response.data
  } catch (error) {
    throw normalizeApiError(error)
  }
}

async function postJson<TResponse, TPayload>(path: string, payload: TPayload): Promise<TResponse> {
  try {
    const response = await apiClient.post<TResponse>(path, payload)
    return response.data
  } catch (error) {
    throw normalizeApiError(error)
  }
}

function normalizeApiError(error: unknown) {
  if (error instanceof AxiosError) {
    const responseMessage = error.response?.data
    if (typeof responseMessage === 'string' && responseMessage.trim()) {
      return new Error(responseMessage)
    }
    if (responseMessage && typeof responseMessage === 'object') {
      const message = 'message' in responseMessage
        ? String(responseMessage.message)
        : JSON.stringify(responseMessage)
      return new Error(message)
    }
    return new Error(error.message || 'Backend request failed.')
  }
  return error
}

export const ewaApi = {
  getOpportunity(opportunityId: string) {
    return requestJson<Opportunity>(`/api/opportunities/${encodeURIComponent(opportunityId)}`)
  },

  getOpportunityRole(opportunityRoleId: string) {
    return requestJson<OpportunityRole>(
      `/api/opportunity-roles/${encodeURIComponent(opportunityRoleId)}`,
    )
  },

  getOpportunityOverlay(opportunityOverlayId: string) {
    return requestJson<OpportunityOverlay>(
      `/api/opportunity-overlays/${encodeURIComponent(opportunityOverlayId)}`,
    )
  },

  getOpportunityOverlayForCandidate(
    opportunityId: string,
    opportunityRoleId: string,
    employeeId: string,
  ) {
    const params = new URLSearchParams({
      opportunityId,
      opportunityRoleId,
      employeeId,
    })
    return requestJson<OpportunityOverlay>(
      `/api/opportunity-overlays/lookup?${params.toString()}`,
    )
  },

  getEmployee(employeeId: string) {
    return requestJson<Employee>(`/api/employees/${encodeURIComponent(employeeId)}`)
  },

  async hydrateRecommendationSelection(
    payload: EwaRecommendationSelectionPayload,
  ): Promise<EwaHydratedSelectionData> {
    const uniqueRoleIds = Array.from(
      new Set(payload.selectedCandidates.map((candidate) => candidate.opportunityRoleId)),
    )
    const uniqueEmployeeIds = Array.from(
      new Set(payload.selectedCandidates.map((candidate) => candidate.employeeId)),
    )

    const [opportunity, roles, employees, overlays] = await Promise.all([
      this.getOpportunity(payload.opportunityId),
      Promise.all(uniqueRoleIds.map((roleId) => this.getOpportunityRole(roleId))),
      Promise.all(uniqueEmployeeIds.map((employeeId) => this.getEmployee(employeeId))),
      Promise.all(
        payload.selectedCandidates.map((candidate) => {
          const opportunityOverlayId = candidate.opportunityOverlayId ?? candidate.overlayId
          if (opportunityOverlayId) {
            return this.getOpportunityOverlay(opportunityOverlayId).catch(() => undefined)
          }
          const candidateOpportunityId = candidate.opportunityId ?? payload.opportunityId
          return this.getOpportunityOverlayForCandidate(
            candidateOpportunityId,
            candidate.opportunityRoleId,
            candidate.employeeId,
          ).catch(() => undefined)
        }),
      ),
    ])

    const rolesById = new Map(roles.map((role) => [role.opportunityRoleId, role]))
    const employeesById = new Map(employees.map((employee) => [employee.employeeId, employee]))

    return {
      opportunity,
      selectedOption: payload.selectedOption,
      selectedCandidates: payload.selectedCandidates.map((candidate, index) => ({
        ...candidate,
        employee: employeesById.get(candidate.employeeId),
        role: rolesById.get(candidate.opportunityRoleId),
        overlay: overlays[index],
      })),
    }
  },

  submitEwaRequest(payload: EwaRequestSubmitRequest) {
    return postJson<EwaRequestSnapshot, EwaRequestSubmitRequest>(
      '/api/ewa-requests/submit',
      payload,
    )
  },
}
