export type Profile = {
  profileId: string
  employeeId: string
  employeeName: string
  profileSummary: string
  keyStrengths: string[]
  preferredWorkTypes: string[]
  domainExperienceSummary: Record<string, string>
  certifications: string[]
  recentHighlights: string[]
  mobilityNotes: string
  languages: string[]
}
