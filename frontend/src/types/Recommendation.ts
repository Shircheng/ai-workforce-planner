export type NumericValue = number | string | null | undefined

export type RecommendationRunMember = {
  opportunityRoleId?: string
  roleName?: string
  employeeId?: string
  employeeName?: string
  rank?: number
  fitStatus?: string
  matchScore?: NumericValue
  capabilityFitScore?: NumericValue
  availabilityFitScore?: NumericValue
  overallStaffingScore?: NumericValue
  availableFteAtStart?: NumericValue
  fteGap?: NumericValue
  earliestFullAvailabilityDate?: string
  rationale?: string
  constraint?: string
}

export type RecommendationOption = {
  optionType?: string
  confidenceScore?: NumericValue
  riskScore?: NumericValue
  riskLevel?: string
  readinessDays?: number
  selectedMemberCount?: number
  risks?: string[]
  members?: RecommendationRunMember[]
}

export type RecommendationRun = {
  recommendationRunId: string
  opportunityId: string
  opportunityName?: string
  generatedAt?: string
  overlayCount?: number
  explanationStatus?: string
  options?: RecommendationOption[]
}

export type MemberExplanation = {
  employeeId?: string
  employeeName?: string
  opportunityRoleId?: string
  roleName?: string
  recommendationNote?: string
  reasoningBullets?: string[]
  riskSummary?: string
  nextActions?: string[]
}

export type OptionExplanation = {
  optionId?: string
  optionType?: string
  optionName?: string
  teamSummary?: string
  reasoningBullets?: string[]
  riskSummary?: string
  nextActions?: string[]
  members?: MemberExplanation[]
}

export type RecommendationRunExplanation = {
  runSummary?: string
  options?: OptionExplanation[]
}

export type RecommendationExplanationResponse = {
  recommendExplanationId?: string
  recommendationRunId?: string
  explanationStatus?: string
  generatedAt?: string
  modelUsed?: string
  fallbackUsed?: boolean
  error?: string
  cached?: boolean
  explanations?: RecommendationRunExplanation
}
