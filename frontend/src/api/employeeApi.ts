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

export const employeeApi = {
  async getTalentPage(
    page: number,
    size = 10,
    filters: TalentFilters,
  ): Promise<EmployeePageResponse> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      skillSearch: filters.skillSearch,
      availability: filters.availability,
      region: filters.region,
      grade: filters.grade,
      status: filters.status,
    })

    const response = await fetch(`${API_BASE_URL}/api/employees?${params}`)

    if (!response.ok) {
      throw new Error('Unable to load talent data')
    }

    return response.json() as Promise<EmployeePageResponse>
  },

  async getPersonProfile(employeeId: string): Promise<PersonProfileResponse> {
    const response = await fetch(
      `${API_BASE_URL}/api/employees/${encodeURIComponent(employeeId)}/profile`,
    )

    if (!response.ok) {
      throw new Error('Unable to load person profile')
    }

    return response.json() as Promise<PersonProfileResponse>
  },

  async getFilterOptions(): Promise<EmployeeFilterOptions> {
    const response = await fetch(`${API_BASE_URL}/api/employees/filter-options`)

    if (!response.ok) {
      throw new Error('Unable to load filter options')
    }

    return response.json() as Promise<EmployeeFilterOptions>
  },
}
