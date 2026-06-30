import axios, { AxiosError } from 'axios'
import type { Opportunity } from '../types/Opportunity'
import type { OpportunityRole } from '../types/OpportunityRoles'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

export type OpportunityRequestPayload = {
  statement: string
  opportunityBrief: string
  opportunityName: string
  clientName: string
  clientType: string
  domain: string
  region: string
  country: string
  city: string
  probability: number | ''
  expectedStartDate: string
  durationWeeks: number | ''
  commercialPriority: string
  timezonePreference: string
}

export type ValidationIssue = {
  field: string
  severity: 'error' | 'warning'
  message: string
}

export type OpportunityParseResponse = {
  opportunity: Opportunity
  roles: OpportunityRole[]
  validationIssues: ValidationIssue[]
  parseSource: 'openai' | 'local-fallback' | 'stored'
}

type ApiErrorPayload = {
  message?: string
  detail?: string
  error?: string
  status?: number
}

function readApiError(error: unknown, fallbackMessage: string) {
  if (error instanceof AxiosError) {
    const responseData = error.response?.data
    if (!responseData) {
      return error.message === 'Network Error' ? 'Failed to fetch' : fallbackMessage
    }

    if (typeof responseData === 'string') {
      const trimmed = responseData.trim()
      if (!trimmed) return fallbackMessage
      if (!trimmed.startsWith('{')) return trimmed

      try {
        const payload = JSON.parse(trimmed) as ApiErrorPayload
        const candidate = payload.message || payload.detail
        if (candidate && !candidate.includes('{"timestamp"')) return candidate
      } catch {
        return fallbackMessage
      }
    }

    if (typeof responseData === 'object') {
      const payload = responseData as ApiErrorPayload
      const candidate = payload.message || payload.detail
      if (candidate && !candidate.includes('{"timestamp"')) return candidate
    }

    return fallbackMessage
  }

  return fallbackMessage
}

export const opportunityApi = {
  async parse(payload: OpportunityRequestPayload): Promise<OpportunityParseResponse> {
    try {
      const response = await apiClient.post<OpportunityParseResponse>('/api/opportunities/parse', {
        ...payload,
        probability: payload.probability === '' ? null : payload.probability,
        durationWeeks: payload.durationWeeks === '' ? null : payload.durationWeeks,
        expectedStartDate: payload.expectedStartDate || null,
      })
      return response.data
    } catch (error) {
      const message = readApiError(error, 'Unable to parse opportunity. Please review the intake fields and try again.')
      throw new Error(message, { cause: error })
    }
  },

  async generateOptionsForRecommender(parsed: OpportunityParseResponse): Promise<OpportunityParseResponse> {
    try {
      const response = await apiClient.post<OpportunityParseResponse>('/api/opportunities/generate-options', {
        opportunity: parsed.opportunity,
        roles: parsed.roles,
      })
      return response.data
    } catch (error) {
      const message = readApiError(error, 'Unable to generate options. Please review the parsed roles and add missing required or desired skills.')
      throw new Error(message, { cause: error })
    }
  },
}
