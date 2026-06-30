import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

export type TalentExplorerEmployee = {
  employeeId: string
  initials: string
  name: string
  grade: string
  role: string
  skills: string[]
  location: string
  availability: string
  expectedReleaseDate: string | null
  domain: string
  department: string
  fitScore: number
}

export type EmployeePageResponse = {
  employees: TalentExplorerEmployee[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type TalentFilters = {
  skillSearch: string
  availability: string
  region: string
  grade: string
  status: string
}

export type EmployeeFilterOptions = {
  grades: string[]
  regions: string[]
}

export type WorkforceDashboardResponse = {
  metrics: DashboardMetric[]
  availabilityOutlook: DashboardBar[]
  supplyByRole: DashboardBar[]
  topSkills: DashboardBar[]
  regions: DashboardBar[]
  demandByDomain: DashboardBar[]
  alerts: DashboardAlert[]
}

export type DashboardMetric = {
  label: string
  value: number
  note: string
}

export type DashboardBar = {
  label: string
  value: number
}

export type DashboardAlert = {
  title: string
  message: string
  tone: 'success' | 'warning' | 'info' | string
}

export type PersonProfileResponse = {
  summary: PersonProfileSummary
  skills: PersonProfileSkill[]
  domains: string[]
  profile: PersonProfileDetails
  allocations: PersonAllocation[]
  projectEvidence: PersonProjectEvidence[]
  availabilityForecast: PersonAvailability[]
}

export type PersonProfileSummary = {
  employeeId: string
  initials: string
  name: string
  grade: string
  role: string
  location: string
  region: string
  country: string
  city: string
  department: string
  discipline: string
  availability: string
  availabilityCategory: string
  currentProject: string
  currentProjectEnd: string | null
  ewaStatus: string
  workMode: string
  currentAllocationFTE: number | null
  availableFTECurrent: number | null
}

export type PersonProfileSkill = {
  skillName: string
  skillCategory: string
  skillLevel: number | null
  yearsExperience: number | null
  lastUsedDate: string | null
  evidenceSource: string
  confidence: string
}

export type PersonProfileDetails = {
  profileSummary: string
  keyStrengths: string[]
  preferredWorkTypes: string[]
  domainExperienceSummary: Record<string, string>
  certifications: string[]
  recentHighlights: string[]
  mobilityNotes: string
  languages: string[]
}

export type PersonAllocation = {
  projectName: string
  clientName: string
  clientType: string
  domain: string
  roleOnProject: string
  allocationFTE: number | null
  startDate: string | null
  plannedEndDate: string | null
  allocationStatus: string
  ewaStatus: string
}

export type PersonProjectEvidence = {
  projectName: string
  clientName: string
  clientType: string
  domain: string
  role: string
  startDate: string | null
  endDate: string | null
  keyTechnologiesOrMethods: string[]
  responsibilities: string[]
  outcomeEvidence: string[]
  region: string
  teamSize: number | null
}

export type PersonAvailability = {
  weekStartDate: string | null
  availableFTE: number | null
  availabilityType: string
  source: string
  confidence: string
  ewaStatus: string
  notes: string
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
})

function normalizeRequestError(error: unknown, fallbackMessage: string) {
  if (error instanceof AxiosError) {
    if (!error.response && error.message === 'Network Error') {
      return new Error('Failed to fetch')
    }
    return new Error(fallbackMessage)
  }
  return error
}

async function getJson<T>(path: string, fallbackMessage: string, config?: AxiosRequestConfig): Promise<T> {
  try {
    const response = await apiClient.get<T>(path, config)
    return response.data
  } catch (error) {
    throw normalizeRequestError(error, fallbackMessage)
  }
}

export const employeeApi = {
  async getTalentPage(
    page: number,
    size = 10,
    filters: TalentFilters,
  ): Promise<EmployeePageResponse> {
    return getJson<EmployeePageResponse>('/api/employees', 'Unable to load talent data', {
      params: {
        page: String(page),
        size: String(size),
        skillSearch: filters.skillSearch,
        availability: filters.availability,
        region: filters.region,
        grade: filters.grade,
        status: filters.status,
      },
    })
  },

  async getPersonProfile(employeeId: string): Promise<PersonProfileResponse> {
    return getJson<PersonProfileResponse>(
      `/api/employees/${encodeURIComponent(employeeId)}/profile`,
      'Unable to load person profile',
    )
  },

  async getFilterOptions(): Promise<EmployeeFilterOptions> {
    return getJson<EmployeeFilterOptions>('/api/employees/filter-options', 'Unable to load filter options')
  },

  async getDashboard(): Promise<WorkforceDashboardResponse> {
    return getJson<WorkforceDashboardResponse>('/api/employees/dashboard', 'Unable to load dashboard data')
  },
}
