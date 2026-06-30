import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

const ANALYTICS_BASE_URL =
  import.meta.env.VITE_ANALYTICS_API_BASE ?? '/python-analysis'

const analyticsClient = axios.create({
  baseURL: ANALYTICS_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

export type FilterOptions = {
  employeeGroupByOptions: string[]
  filters: {
    regions: string[]
    countries: string[]
    cities: string[]
    grades: string[]
    roles: string[]
    disciplines: string[]
    domains: string[]
    availabilityCategories: string[]
    workModes: string[]
    skillCategories: string[]
  }
}

export type SkillGapRequest = {
  requiredSkills: string[]
  minSkillLevel: number
  groupBy?: string
  filters?: {
    region?: string
    country?: string
    city?: string
    grade?: string
    role?: string
    domain?: string
    availabilityCategory?: string
    workMode?: string
    skillCategory?: string
  }
}

export type SkillGap = {
  skillName: string
  skillCategory?: string
  matchedEmployeeCount: number
  missingEmployeeCount: number
  coveragePercentage: number
}

export type GroupedInsight = {
  groupBy: string
  groupValue: string
  employeeCount: number
  totalMissingSkillCount: number
  averageFitPercentage: number
}

export type GroupedSkillCoverage = {
  groupBy: string
  groupValue: string
  skillName: string
  skillCategory?: string
  matchedEmployeeCount: number
  missingEmployeeCount: number
  coveragePercentage: number
}

export type SkillGapResponse = {
  totalEmployeesEvaluated: number
  totalRequiredSkillCount: number
  averageFitPercentage: number
  readyCandidateCount: number
  skillGaps: SkillGap[]
  groupedInsights: GroupedInsight[]
  groupedSkillCoverage: GroupedSkillCoverage[]
}

export type WorkforceForecastRequest = {
  asOfDate?: string
  horizons?: number[]
  groupBy?: string
  filters?: SkillGapRequest['filters']
}

export type WorkforceGroupedAvailability = {
  groupBy: string
  groupValue: string
  availableEmployeeCount: number
  totalAvailableFte: number
}

export type WorkforceForecastBucket = {
  horizonDays: number
  asOfDate: string
  cutoffDate: string
  availableEmployeeCount: number
  totalAvailableFte: number
  groupedAvailability?: WorkforceGroupedAvailability[]
}

export type WorkforceForecastResponse = {
  totalEmployeesEvaluated: number
  forecast: WorkforceForecastBucket[]
}

export type OpportunityForecastRequest = {
  asOfDate?: string
  horizons?: number[]
  filters?: {
    region?: string
    country?: string
    city?: string
    domain?: string
    stage?: string
    commercialPriority?: string
    deliveryRisk?: string
  }
}

export type OpportunityForecastSummary = {
  totalOpportunities: number
  totalRequiredFte: number
  probabilityForecastFte: number
  expectedWorkloadFteWeeks: number
  forecastWindowDays: number
  forecastWindowWeeks: number
  highPriorityOpportunities: number
  highRiskDemandFte: number
  topSkillName: string
  topSkillForecastFte: number
}

export type OpportunityDemandWindow = {
  window: string
  requiredFte: number
  forecastFte: number
  mainSkills: string[]
}

export type OpportunitySkillDemandRow = {
  skillName: string
  requiredFte: number
  forecastFte: number
  opportunityCount: number
}

export type OpportunityForecastRow = {
  opportunityName: string
  startDate: string
  probability: number
  requiredFte: number
  forecastFte: number
  deliveryRisk: string
  skills: string[]
}

export type OpportunityForecastResponse = {
  asOfDate: string
  summary: OpportunityForecastSummary
  demandWindows: OpportunityDemandWindow[]
  skillDemand: OpportunitySkillDemandRow[]
  opportunities: OpportunityForecastRow[]
}

function normalizeRequestError(error: unknown) {
  if (error instanceof AxiosError) {
    const responseData = error.response?.data
    if (typeof responseData === 'string' && responseData.trim()) {
      return new Error(responseData)
    }
    if (responseData) {
      return new Error(JSON.stringify(responseData))
    }
    return new Error(error.message === 'Network Error' ? 'Failed to fetch' : error.message)
  }
  return error
}

async function requestJson<T>(path: string, config?: AxiosRequestConfig): Promise<T> {
  try {
    const response = await analyticsClient.request<T>({
      url: path,
      method: 'GET',
      ...config,
    })
    return response.data
  } catch (error) {
    throw normalizeRequestError(error)
  }
}

export const analysisApi = {
  filterOptions() {
    return requestJson<FilterOptions>('/analysis/filter-options')
  },

  skillGap(payload: SkillGapRequest) {
    return requestJson<SkillGapResponse>('/analysis/skill-gap', {
      method: 'POST',
      data: payload,
    })
  },

  workforceForecast(payload: WorkforceForecastRequest) {
    return requestJson<WorkforceForecastResponse>('/analysis/workforce-forecast', {
      method: 'POST',
      data: payload,
    })
  },

  opportunityForecast(payload: OpportunityForecastRequest) {
    return requestJson<OpportunityForecastResponse>('/analysis/opportunity-forecast', {
      method: 'POST',
      data: payload,
    })
  },
}
