import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { SkillGap } from '../../../api/analysisApi'

type SkillCoveragePanelProps = {
  skillGaps: SkillGap[]
}

function SkillCoveragePanel({ skillGaps }: SkillCoveragePanelProps) {
  const chartRows = skillGaps.map((skill) => ({
    ...skill,
    skillLabel: skill.skillCategory
      ? `${skill.skillName} (${skill.skillCategory})`
      : skill.skillName,
  }))

  return (
    <div className="analysis-panel analysis-panel-large">
      <div className="analysis-panel-header">
        <h2>Skill Coverage (%)</h2>
      </div>
      <div className="analysis-chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={chartRows}
            layout="vertical"
            margin={{ top: 8, right: 20, bottom: 8, left: 8 }}
          >
            <CartesianGrid strokeDasharray="3 3" horizontal={false} />
            <XAxis type="number" domain={[0, 100]} />
            <YAxis dataKey="skillLabel" type="category" width={150} />
            <Tooltip />
            <Bar
              dataKey="coveragePercentage"
              name="Coverage %"
              fill="#2563eb"
              radius={[0, 5, 5, 0]}
              barSize={18}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

export default SkillCoveragePanel
