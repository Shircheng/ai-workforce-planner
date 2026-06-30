import { useEffect, useMemo, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { RefreshCw } from 'lucide-react'
import type {
  OpportunityDemandWindow,
  OpportunityForecastResponse,
  OpportunitySkillDemandRow,
} from '../../../api/analysisApi'
import { analysisApi } from '../../../api/analysisApi'
import AnalysisEmptyState from '../shared/AnalysisEmptyState'
import AnalysisHeader from '../shared/AnalysisHeader'
import { chartColors } from '../shared/analysisModel'

const skillDemandColors = [
  '#2563eb',
  '#10b981',
  '#f59e0b',
  '#ec4899',
  '#8b5cf6',
  '#06b6d4',
  '#84cc16',
  '#f97316',
]

function OpportunityForecastView() {
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [horizons, setHorizons] = useState('30, 60, 90')
  const [result, setResult] = useState<OpportunityForecastResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  const horizonDays = useMemo(
    () =>
      horizons
        .split(',')
        .map((value) => Number(value.trim()))
        .filter((value) => Number.isFinite(value) && value > 0),
    [horizons],
  )

  useEffect(() => {
    void runForecast()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function runForecast() {
    if (horizonDays.length === 0) {
      setError('Add at least one forecast horizon.')
      return
    }

    setIsLoading(true)
    setError('')

    try {
      const response = await analysisApi.opportunityForecast({
        asOfDate,
        horizons: horizonDays,
      })
      setResult(response)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to load opportunity forecast.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  const summary = result?.summary
  const fallbackWindowDays = Math.max(...horizonDays, 0)
  const forecastWindowDays = summary?.forecastWindowDays ?? fallbackWindowDays
  const forecastWindowWeeks = summary?.forecastWindowWeeks ?? Number((fallbackWindowDays / 7).toFixed(1))
  const hasNoData =
    !isLoading &&
    !error &&
    result !== null &&
    ((summary?.totalOpportunities ?? 0) === 0 || result.skillDemand.length === 0)

  return (
    <>
      <AnalysisHeader
        title="Opportunity Forecast"
        eyebrow="Pipeline demand planning"
        metaItems={
          hasNoData
            ? []
            : [
                `${summary?.totalOpportunities ?? 0} opportunities`,
                `${horizonDays.length} windows`,
              ]
        }
      />

      {!hasNoData ? (
        <section className="analysis-command-card" aria-label="Opportunity forecast controls">
          <div className="analysis-command-bar analysis-opportunity-command-bar">
            <label className="analysis-field">
              <span>As of date</span>
              <input
                type="date"
                value={asOfDate}
                onChange={(event) => setAsOfDate(event.target.value)}
              />
            </label>

            <label className="analysis-skill-input">
              <span>Forecast windows</span>
              <input
                value={horizons}
                onChange={(event) => setHorizons(event.target.value)}
                placeholder="30, 60, 90"
              />
            </label>

            <button
              className="analysis-run-button"
              type="button"
              onClick={() => void runForecast()}
              disabled={isLoading}
            >
              <RefreshCw size={16} aria-hidden="true" />
              <span>{isLoading ? 'Running' : 'Run'}</span>
            </button>
          </div>
        </section>
      ) : null}

      {error ? <div className="analysis-error">{error}</div> : null}

      {hasNoData ? (
        <AnalysisEmptyState
          title="No opportunity forecast data yet"
          message="Import opportunity and role data from the Dashboard, then run the opportunity forecast again."
        />
      ) : (
        <>
          <section
            className="analysis-overview analysis-opportunity-summary"
            aria-label="Opportunity forecast summary"
          >
            <OpportunityMetric label="Total Required" value={`${summary?.totalRequiredFte ?? 0} FTE`} />
            <OpportunityMetric
              label="Probability Forecast"
              value={`${summary?.probabilityForecastFte ?? 0} FTE`}
            />
            <OpportunityMetric
              label="Expected Workload"
              value={`${summary?.expectedWorkloadFteWeeks ?? 0}`}
              detail={
                summary
                  ? `FTE-weeks over ${forecastWindowDays} days (${forecastWindowWeeks} weeks)`
                  : 'FTE-weeks'
              }
            />
            <OpportunityMetric
              label="High Priority"
              value={`${summary?.highPriorityOpportunities ?? 0} opportunities`}
            />
            <OpportunityMetric
              label="High Risk Demand"
              value={`${summary?.highRiskDemandFte ?? 0} FTE`}
            />
            <OpportunityMetric
              label="Top Skill"
              value={summary ? `${summary.topSkillName}: ${summary.topSkillForecastFte} FTE` : '-'}
            />
          </section>

          <section className="analysis-opportunity-visual-grid">
            <DemandWindowPanel rows={result?.demandWindows ?? []} />
            <SkillDemandPanel rows={result?.skillDemand ?? []} />
          </section>
        </>
      )}
    </>
  )
}

function OpportunityMetric({
  label,
  value,
  detail,
}: {
  label: string
  value: string
  detail?: string
}) {
  return (
    <div className="analysis-kpi">
      <span>{label}</span>
      <strong>{value}</strong>
      {detail ? <small>{detail}</small> : null}
    </div>
  )
}

function DemandWindowPanel({ rows }: { rows: OpportunityDemandWindow[] }) {
  return (
    <section className="analysis-panel analysis-window-panel">
      <div className="analysis-panel-header">
        <h2>Demand Forecast by Time Window</h2>
      </div>
      <div className="analysis-chart analysis-window-chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 12, bottom: 8, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="window" />
            <YAxis />
            <Tooltip />
            <Legend />
            <Bar
              dataKey="requiredFte"
              name="Required FTE"
              fill={chartColors[0]}
              radius={[5, 5, 0, 0]}
            />
            <Bar
              dataKey="forecastFte"
              name="Probability forecast FTE"
              fill={chartColors[1]}
              radius={[5, 5, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="analysis-window-skills" aria-label="Main skills by window">
        {rows.map((row) => (
          <div key={row.window} className="analysis-window-skill-row">
            <strong>{row.window}</strong>
            <span>{row.mainSkills.join(', ') || 'No demand'}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

function SkillDemandPanel({ rows }: { rows: OpportunitySkillDemandRow[] }) {
  const topRows = rows.slice(0, 8)

  return (
    <section className="analysis-panel analysis-skill-demand-panel">
      <div className="analysis-panel-header">
        <h2>Top Skills Demand Forecast</h2>
      </div>
      <div className="analysis-skill-demand-composition">
        <div className="analysis-chart analysis-skill-demand-donut">
          <div className="analysis-donut-center">
            <strong>Top Skills</strong>
          </div>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Tooltip />
              <Pie
                data={topRows}
                dataKey="forecastFte"
                nameKey="skillName"
                innerRadius="60%"
                outerRadius="92%"
                paddingAngle={2}
              >
                {topRows.map((row, index) => (
                  <Cell
                    key={row.skillName}
                    fill={skillDemandColors[index % skillDemandColors.length]}
                  />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="analysis-skill-demand-ranking">
          {topRows.map((row, index) => (
            <div className="analysis-skill-rank-row" key={row.skillName}>
              <span
                className="analysis-skill-rank-dot"
                style={{ background: skillDemandColors[index % skillDemandColors.length] }}
              />
              <div>
                <strong>{row.skillName}</strong>
                <span>
                  {row.forecastFte} forecast FTE / {row.requiredFte} required FTE
                </span>
              </div>
              <em>{row.opportunityCount} opportunities</em>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default OpportunityForecastView
