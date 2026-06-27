export type OpportunityRole = {
  opportunityRoleId: string
  opportunityId: string
  roleName: string
  disciplineOrDepartment: string
  gradePreference: string
  requiredSkills: string
  desiredSkills: string
  domainExperienceRequired: string
  locationPreference: string
  startDate: string
  durationWeeks: number
  fteRequired: number
  priority: string
  flexibilityNotes: string
  minimumIndividualFTE: number
  canCombineCandidates: 'Yes' | 'No'
}
