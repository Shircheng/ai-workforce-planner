import { useEffect, useMemo, useState } from 'react'
import type { FilterOptions, SkillGapResponse } from '../../../api/analysisApi'
import { analysisApi } from '../../../api/analysisApi'
import AnalysisControls from '../shared/AnalysisControls'
import AnalysisEmptyState from '../shared/AnalysisEmptyState'
import AnalysisFilterPanel from '../shared/AnalysisFilterPanel'
import AnalysisHeader from '../shared/AnalysisHeader'
import AnalysisSummary from '../shared/AnalysisSummary'
import GroupCoveragePanel from './GroupCoveragePanel'
import GroupedInsightPanel from './GroupedInsightPanel'
import PriorityGapsPanel from './PriorityGapsPanel'
import SkillCoveragePanel from './SkillCoveragePanel'
import {
  buildGroupedCoverageRows,
  compactFilters,
  emptySkillGapFilters,
  fallbackGroupByOptions,
  labelize,
  sortSkillGaps,
  type SkillGapFilters,
} from '../shared/analysisModel'

type SkillGapViewProps = {
  filterOptions: FilterOptions | null
}

function SkillGapView({ filterOptions }: SkillGapViewProps) {
  const [requiredSkills, setRequiredSkills] = useState('React, Java, AWS')
  const [minSkillLevel, setMinSkillLevel] = useState(3)
  const [groupBy, setGroupBy] = useState('region')
  const [filters, setFilters] = useState<SkillGapFilters>(emptySkillGapFilters)
  const [result, setResult] = useState<SkillGapResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isFilterOpen, setIsFilterOpen] = useState(false)
  const [error, setError] = useState('')

  const skillNames = useMemo(
    () =>
      requiredSkills
        .split(',')
        .map((skill) => skill.trim())
        .filter(Boolean),
    [requiredSkills],
  )

  const groupByOptions =
    filterOptions?.employeeGroupByOptions?.length
      ? filterOptions.employeeGroupByOptions
      : fallbackGroupByOptions

  const groupedCoverageRows = useMemo(
    () => buildGroupedCoverageRows(result?.groupedSkillCoverage ?? []),
    [result],
  )

  const sortedSkillGaps = useMemo(
    () => sortSkillGaps(result?.skillGaps ?? []),
    [result],
  )

  const lowestCoverageSkill = sortedSkillGaps[0]
  const activeFilterCount = Object.values(filters).filter(Boolean).length
  const hasNoData =
    !isLoading &&
    !error &&
    result !== null &&
    (result.totalEmployeesEvaluated === 0 || result.skillGaps.length === 0)

  useEffect(() => {
    void runSkillGap()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function runSkillGap() {
    if (skillNames.length === 0) {
      setError('Add at least one required skill.')
      return
    }

    setIsLoading(true)
    setError('')

    try {
      const response = await analysisApi.skillGap({
        requiredSkills: skillNames,
        minSkillLevel,
        groupBy,
        filters: compactFilters(filters),
      })
      setResult(response)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to load skill gap analysis.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  function updateFilter(name: keyof SkillGapFilters, value: string) {
    setFilters((current) => ({ ...current, [name]: value }))
  }

  function clearFilters() {
    setFilters(emptySkillGapFilters)
  }

  return (
    <>
      <AnalysisHeader
        title="Skill Gap Analysis"
        eyebrow="Capability coverage"
        metaItems={
          hasNoData
            ? []
            : [
                `${skillNames.length} skills`,
                `Level ${minSkillLevel}+`,
                `Group: ${labelize(groupBy)}`,
                ...(filters.skillCategory ? [filters.skillCategory] : []),
              ]
        }
      />

      {!hasNoData ? (
        <AnalysisControls
          requiredSkills={requiredSkills}
          activeFilterCount={activeFilterCount}
          isLoading={isLoading}
          onRequiredSkillsChange={setRequiredSkills}
          onToggleFilters={() => setIsFilterOpen((current) => !current)}
          onRun={() => void runSkillGap()}
        />
      ) : null}

      {!hasNoData && isFilterOpen ? (
        <AnalysisFilterPanel
          filterOptions={filterOptions}
          filters={filters}
          groupBy={groupBy}
          groupByOptions={groupByOptions}
          minSkillLevel={minSkillLevel}
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
          onFilterChange={updateFilter}
          onGroupByChange={setGroupBy}
          onMinSkillLevelChange={setMinSkillLevel}
        />
      ) : null}

      {error ? <div className="analysis-error">{error}</div> : null}

      {hasNoData ? (
        <AnalysisEmptyState
          title="No skill gap data yet"
          message="Import your workforce Excel dataset from the Dashboard, then run the skill gap analysis again."
        />
      ) : (
        <>
          <AnalysisSummary
            averageFitPercentage={result?.averageFitPercentage ?? 0}
            employeesEvaluated={result?.totalEmployeesEvaluated ?? 0}
            priorityGap={lowestCoverageSkill}
            readyCandidateCount={result?.readyCandidateCount ?? 0}
          />

          <section className="analysis-dashboard-grid">
            <SkillCoveragePanel skillGaps={result?.skillGaps ?? []} />
            <PriorityGapsPanel skillGaps={sortedSkillGaps} />
          </section>

          <section className="analysis-lower-grid">
            <GroupCoveragePanel
              groupBy={groupBy}
              rows={groupedCoverageRows}
              skillNames={skillNames}
            />
            <GroupedInsightPanel
              groupBy={groupBy}
              insights={result?.groupedInsights ?? []}
            />
          </section>
        </>
      )}
    </>
  )
}

export default SkillGapView
