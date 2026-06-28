import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  chartColors,
  type CoverageChartRow,
  labelize,
} from '../shared/analysisModel'

type GroupCoveragePanelProps = {
  groupBy: string
  rows: CoverageChartRow[]
  skillNames: string[]
}

function GroupCoveragePanel({
  groupBy,
  rows,
  skillNames,
}: GroupCoveragePanelProps) {
  return (
    <div className="analysis-panel">
      <div className="analysis-panel-header">
        <h2>{labelize(groupBy)} Coverage (%)</h2>
      </div>
      <div className="analysis-chart analysis-chart-wide">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="groupValue" />
            <YAxis domain={[0, 100]} />
            <Tooltip />
            <Legend />
            {skillNames.map((skillName, index) => (
              <Bar
                key={skillName}
                dataKey={skillName}
                fill={chartColors[index % chartColors.length]}
                radius={[5, 5, 0, 0]}
                barSize={24}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

export default GroupCoveragePanel
