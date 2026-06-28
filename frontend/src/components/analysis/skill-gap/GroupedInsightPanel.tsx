import type { GroupedInsight } from '../../../api/analysisApi'
import { clampPercent, formatPercent, labelize } from '../shared/analysisModel'

type GroupedInsightPanelProps = {
  groupBy: string
  insights: GroupedInsight[]
}

function GroupedInsightPanel({ groupBy, insights }: GroupedInsightPanelProps) {
  return (
    <div className="analysis-panel">
      <div className="analysis-panel-header">
        <h2>Grouped Insight</h2>
      </div>
      <div className="analysis-table-wrap">
        <table className="analysis-table">
          <thead>
            <tr>
              <th>{labelize(groupBy)}</th>
              <th>Employees</th>
              <th>Average fit</th>
            </tr>
          </thead>
          <tbody>
            {insights.map((item) => (
              <tr key={item.groupValue}>
                <td>{item.groupValue}</td>
                <td>{item.employeeCount}</td>
                <td>
                  <div className="analysis-table-meter">
                    <span>{formatPercent(item.averageFitPercentage)}</span>
                    <div className="analysis-mini-track">
                      <span
                        style={{
                          width: `${clampPercent(item.averageFitPercentage)}%`,
                        }}
                      />
                    </div>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default GroupedInsightPanel
