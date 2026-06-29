import type {
  GroupedSkillCoverage,
  SkillGap,
} from '../../../api/analysisApi'

export const fallbackGroupByOptions = [
  'region',
  'domain',
  'grade',
  'role',
  'location',
]

export const chartColors = ['#2563eb', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444']

export type SkillGapFilters = {
  skillCategory: string
  region: string
  domain: string
  grade: string
  role: string
  availabilityCategory: string
}

export type WorkforceForecastFilters = {
  region: string
  domain: string
  grade: string
  role: string
  availabilityCategory: string
}

export type CoverageChartRow = {
  groupValue: string
} & Record<string, string | number>

export const emptySkillGapFilters: SkillGapFilters = {
  skillCategory: '',
  region: '',
  domain: '',
  grade: '',
  role: '',
  availabilityCategory: '',
}

export const emptyWorkforceForecastFilters: WorkforceForecastFilters = {
  region: '',
  domain: '',
  grade: '',
  role: '',
  availabilityCategory: '',
}

export function buildGroupedCoverageRows(
  coverageItems: GroupedSkillCoverage[] = [],
) {
  const rows = new Map<string, CoverageChartRow>()

  coverageItems.forEach((item) => {
    const row = rows.get(item.groupValue) ?? { groupValue: item.groupValue }
    row[item.skillName] = item.coveragePercentage
    rows.set(item.groupValue, row)
  })

  return [...rows.values()]
}

export function sortSkillGaps(skillGaps: SkillGap[] = []) {
  return [...skillGaps].sort(
    (left, right) => left.coveragePercentage - right.coveragePercentage,
  )
}

export function compactFilters<T extends object>(filters: T) {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== ''),
  ) as Partial<T>
}

export function formatPercent(value: number) {
  return `${Number(value.toFixed(2))}%`
}

export function clampPercent(value: number) {
  return Math.min(100, Math.max(0, value))
}

export function labelize(value: string) {
  return value
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (letter) => letter.toUpperCase())
}
