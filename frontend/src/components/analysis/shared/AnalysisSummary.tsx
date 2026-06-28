import { Info } from 'lucide-react'
import type { SkillGap } from '../../../api/analysisApi'
import { clampPercent, formatPercent } from './analysisModel'

type AnalysisSummaryProps = {
  averageFitPercentage: number
  employeesEvaluated: number
  priorityGap?: SkillGap
  readyCandidateCount: number
}

function AnalysisSummary({
  averageFitPercentage,
  employeesEvaluated,
  priorityGap,
  readyCandidateCount,
}: AnalysisSummaryProps) {
  return (
    <section className="analysis-overview" aria-label="Skill gap summary">
      <div className="analysis-score-card">
        <div>
          <span>Average fit</span>
          <strong>{formatPercent(averageFitPercentage)}</strong>
        </div>
        <div className="analysis-progress-track">
          <span style={{ width: `${clampPercent(averageFitPercentage)}%` }} />
        </div>
      </div>
      <KpiCard label="Employees evaluated" value={employeesEvaluated} />
      <KpiCard
        label="Priority gap"
        value={priorityGap?.skillName ?? '-'}
        detail={
          priorityGap
            ? `${formatPercent(priorityGap.coveragePercentage)} coverage`
            : 'No skill gap yet'
        }
      />
      <KpiCard
        label="Ready candidates"
        value={readyCandidateCount}
        detail="matched all required skills"
        tooltip="Employees who match every required skill at the selected minimum skill level and filters."
      />
    </section>
  )
}

function KpiCard({
  label,
  value,
  detail,
  tooltip,
}: {
  label: string
  value: string | number
  detail?: string
  tooltip?: string
}) {
  return (
    <div className="analysis-kpi">
      <span className="analysis-kpi-label">
        {label}
        {tooltip ? (
          <span className="analysis-tooltip">
            <Info size={14} aria-hidden="true" />
            <span className="analysis-tooltip-text">{tooltip}</span>
          </span>
        ) : null}
      </span>
      <strong>{value}</strong>
      {detail ? <small>{detail}</small> : null}
    </div>
  )
}

export default AnalysisSummary
