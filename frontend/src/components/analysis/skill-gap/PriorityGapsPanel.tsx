import type { SkillGap } from '../../../api/analysisApi'
import { clampPercent, formatPercent } from '../shared/analysisModel'

type PriorityGapsPanelProps = {
  skillGaps: SkillGap[]
}

function PriorityGapsPanel({ skillGaps }: PriorityGapsPanelProps) {
  return (
    <div className="analysis-panel">
      <div className="analysis-panel-header">
        <h2>Priority Gaps</h2>
      </div>
      <div className="analysis-gap-list">
        {skillGaps.map((skill) => (
          <SkillGapRow key={skill.skillName} skill={skill} />
        ))}
      </div>
    </div>
  )
}

function SkillGapRow({ skill }: { skill: SkillGap }) {
  return (
    <div className="analysis-gap-row">
      <div className="analysis-gap-row-header">
        <div className="analysis-gap-title">
          <strong>{skill.skillName}</strong>
          {skill.skillCategory ? (
            <span className="analysis-skill-category">{skill.skillCategory}</span>
          ) : null}
        </div>
        <span>{formatPercent(skill.coveragePercentage)}</span>
      </div>
      <div className="analysis-progress-track">
        <span style={{ width: `${clampPercent(skill.coveragePercentage)}%` }} />
      </div>
      <div className="analysis-gap-meta">
        <span>{skill.matchedEmployeeCount} matched employees</span>
      </div>
    </div>
  )
}

export default PriorityGapsPanel
