import type {
  NumericValue,
  OptionExplanation,
  RecommendationExplanationResponse,
  RecommendationOption,
  RecommendationRun,
  RecommendationRunMember,
} from '../../types/Recommendation'

export const notAvailable = 'Not available'

const optionOrder = [
  'BEST_SKILL_FIT',
  'FASTEST_AVAILABLE_TEAM',
  'BALANCED_LOW_RISK_TEAM',
]

const optionCopy: Record<
  string,
  { label: string; shortLabel: string; accent: string }
> = {
  BEST_SKILL_FIT: {
    label: 'Option A: Best Skill Fit',
    shortLabel: 'Option A',
    accent: 'green',
  },
  FASTEST_AVAILABLE_TEAM: {
    label: 'Option B: Fastest Available',
    shortLabel: 'Option B',
    accent: 'blue',
  },
  BALANCED_LOW_RISK_TEAM: {
    label: 'Option C: Balanced Low Risk',
    shortLabel: 'Option C',
    accent: 'orange',
  },
}

export function firstOptionType(run: RecommendationRun | null) {
  return sortOptions(run?.options ?? [])[0]?.optionType ?? null
}

export function sortOptions(options: RecommendationOption[]) {
  return [...options].sort((left, right) => {
    const leftIndex = optionOrder.indexOf(left.optionType ?? '')
    const rightIndex = optionOrder.indexOf(right.optionType ?? '')
    return normalizeOrder(leftIndex) - normalizeOrder(rightIndex)
  })
}

function normalizeOrder(index: number) {
  return index === -1 ? 99 : index
}

export function optionConfig(option: RecommendationOption, index: number) {
  if (option.optionType && optionCopy[option.optionType]) {
    return optionCopy[option.optionType]
  }

  const labels = ['Option A', 'Option B', 'Option C']
  const accents = ['green', 'blue', 'orange']
  const label = `${labels[index] ?? `Option ${index + 1}`}: ${titleCase(
    option.optionType ?? 'Recommendation',
  )}`

  return {
    label,
    shortLabel: labels[index] ?? `Option ${index + 1}`,
    accent: accents[index] ?? 'green',
  }
}

export function findOptionExplanation(
  response: RecommendationExplanationResponse | null,
  option: RecommendationOption,
) {
  return response?.explanations?.options?.find(
    (item) => item.optionType === option.optionType,
  )
}

export function findMemberExplanation(
  response: RecommendationExplanationResponse | null,
  option: RecommendationOption,
  member: RecommendationRunMember,
) {
  const optionExplanation = findOptionExplanation(response, option)
  return optionExplanation?.members?.find(
    (item) =>
      item.employeeId === member.employeeId &&
      item.opportunityRoleId === member.opportunityRoleId,
  )
}

export function toNumber(value: NumericValue) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

export function formatScore(value: NumericValue) {
  const score = toNumber(value)
  return score === null ? '--' : Math.round(score).toString()
}

export function formatNumber(value: NumericValue) {
  const number = toNumber(value)
  if (number === null) {
    return notAvailable
  }
  return number.toLocaleString(undefined, {
    maximumFractionDigits: 1,
    minimumFractionDigits: Number.isInteger(number) ? 0 : 1,
  })
}

export function formatDate(value?: string) {
  if (!value) {
    return notAvailable
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date)
}

export function readinessText(option: RecommendationOption) {
  if (option.readinessDays === undefined || option.readinessDays === null) {
    return notAvailable
  }
  return option.readinessDays === 0
    ? 'Ready now'
    : `${option.readinessDays} day${option.readinessDays === 1 ? '' : 's'}`
}

export function readinessPill(option: RecommendationOption) {
  const days = option.readinessDays
  if (days === undefined || days === null) {
    return { label: 'Readiness unavailable', tone: 'neutral' }
  }

  if (days === 0) {
    return { label: 'Ready now', tone: 'strong' }
  }

  const hasOpenGap = optionFteGap(option) > 0
  const hasOpenConstraint = (option.members ?? []).some(hasConstraint)
  const mostlyReady = hasOpenGap || hasOpenConstraint
  const tone = days <= 30 && !mostlyReady ? 'strong' : 'warn'
  const prefix = mostlyReady ? 'Mostly ready' : 'Ready'

  return {
    label: `${prefix} in ${days} day${days === 1 ? '' : 's'}`,
    tone,
  }
}

export function availabilityText(member: RecommendationRunMember) {
  return member.earliestFullAvailabilityDate
    ? formatDate(member.earliestFullAvailabilityDate)
    : formatNumber(toNumber(member.availableFteAtStart))
}

export function memberSkillEvidenceLines(member: RecommendationRunMember) {
  const rationaleLines = splitEvidenceLines(member.rationale)
  if (rationaleLines.length) {
    return rationaleLines
  }

  const fallbackLines = [
    member.fitStatus ? `Fit status: ${member.fitStatus}` : null,
    `${formatNumber(toNumber(member.availableFteAtStart))} FTE available at start`,
    `FTE gap: ${formatNumber(toNumber(member.fteGap))}`,
  ]

  return fallbackLines.filter((line): line is string => Boolean(line))
}

function splitEvidenceLines(value?: string) {
  if (!value?.trim()) {
    return []
  }

  return value
    .split(';')
    .map((line) => line.replace(/\s+/g, ' ').trim())
    .filter((line) => Boolean(line) && !isScoreOnlyEvidenceLine(line))
}

function isScoreOnlyEvidenceLine(value: string) {
  return /^(capability|availability|overall)(?:\s+(?:score|fit))?\s*[:=-]?\s*\d+(?:\.\d+)?\.?$/i.test(
    value,
  )
}

export function memberSkillScorePills(member: RecommendationRunMember) {
  return [
    {
      label: 'Capability',
      value: toNumber(member.capabilityFitScore ?? member.matchScore),
    },
    { label: 'Availability', value: toNumber(member.availabilityFitScore) },
    { label: 'Overall', value: toNumber(member.overallStaffingScore) },
  ]
    .filter((score) => score.value !== null)
    .map((score) => ({
      label: score.label,
      value: formatScore(score.value),
    }))
}

export function teamRiskItems(
  option: RecommendationOption,
  explanation?: OptionExplanation,
) {
  const aiRiskItems = splitTextItems(explanation?.riskSummary)
  if (aiRiskItems.length) {
    return aiRiskItems.slice(0, 4)
  }

  return riskItems(option)
}

function splitTextItems(value?: string) {
  if (!value?.trim()) {
    return []
  }

  return value
    .split(/\n|;/)
    .map((item) => item.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
}

export function normalizedList(items?: string[] | null) {
  const values = items
    ?.map((item) => item?.trim())
    .filter((item): item is string => Boolean(item)) ?? []

  return Array.from(new Set(values))
}

export function hasSkillEvidence(source: {
  skillCoverageScore?: NumericValue
  matchedRequiredSkills?: string[] | null
  missingRequiredSkills?: string[] | null
  matchedDesiredSkills?: string[] | null
  missingDesiredSkills?: string[] | null
}) {
  return (
    toNumber(source.skillCoverageScore) !== null ||
    normalizedList(source.matchedRequiredSkills).length > 0 ||
    normalizedList(source.missingRequiredSkills).length > 0 ||
    normalizedList(source.matchedDesiredSkills).length > 0 ||
    normalizedList(source.missingDesiredSkills).length > 0
  )
}

export function riskItems(option: RecommendationOption) {
  return option.risks?.filter(Boolean).slice(0, 4) ?? []
}

export function nextActionItems(explanation?: OptionExplanation) {
  return explanation?.nextActions?.filter(Boolean).slice(0, 4) ?? []
}

export function optionFteGap(option: RecommendationOption) {
  return (option.members ?? []).reduce(
    (sum, member) => sum + (toNumber(member.fteGap) ?? 0),
    0,
  )
}

export function bestMemberScore(member: RecommendationRunMember) {
  return toNumber(member.overallStaffingScore ?? member.matchScore)
}

export function optionMatchScore(option: RecommendationOption) {
  const scores = (option.members ?? [])
    .map(bestMemberScore)
    .filter((score): score is number => score !== null)

  if (!scores.length) {
    return toNumber(option.confidenceScore)
  }

  const average = scores.reduce((sum, score) => sum + score, 0) / scores.length
  return Math.round(average)
}

export function scorePercent(score: number | null) {
  if (score === null) {
    return 0
  }
  return Math.max(0, Math.min(100, Math.round(score)))
}

export function availabilityStatus(member: RecommendationRunMember) {
  const fteAtStart = toNumber(member.availableFteAtStart)
  const fteGap = toNumber(member.fteGap)

  if (fteAtStart === null) {
    return { label: 'Unknown', tone: 'neutral' }
  }

  if (fteAtStart <= 0) {
    return { label: 'No start FTE', tone: 'warn' }
  }

  if (fteGap !== null && fteGap > 0) {
    return { label: 'Partial', tone: 'medium' }
  }

  return { label: 'Ready', tone: 'strong' }
}

export function hasConstraint(member: RecommendationRunMember) {
  const constraint = member.constraint?.trim().toLowerCase()
  const fteGap = toNumber(member.fteGap)
  return Boolean(
    (constraint && constraint !== 'none') || (fteGap !== null && fteGap > 0),
  )
}

export function scoreTone(score: number | null, constrained: boolean) {
  if (constrained || score === null || score < 75) {
    return 'warn'
  }
  if (score >= 85) {
    return 'strong'
  }
  return 'medium'
}

export function initials(name?: string) {
  if (!name) {
    return 'NA'
  }
  const parts = name.trim().split(/\s+/).slice(0, 2)
  return parts.map((part) => part[0]?.toUpperCase()).join('') || 'NA'
}

function titleCase(value: string) {
  return value
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase())
}
