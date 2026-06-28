import { useEffect, useMemo, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { RefreshCw, SlidersHorizontal, X } from 'lucide-react'
import type {
  FilterOptions,
  WorkforceForecastBucket,
  WorkforceForecastResponse,
} from '../../../api/analysisApi'
import { analysisApi } from '../../../api/analysisApi'
import AnalysisHeader from '../shared/AnalysisHeader'
import {
  compactFilters,
  emptyWorkforceForecastFilters,
  fallbackGroupByOptions,
  labelize,
  type WorkforceForecastFilters,
} from '../shared/analysisModel'

type WorkforceForecastViewProps = {
  filterOptions: FilterOptions | null
}

type GroupedForecastRow = {
  groupValue: string
} & Record<string, string | number>

function WorkforceForecastView({ filterOptions }: WorkforceForecastViewProps) {
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [horizons, setHorizons] = useState('30, 60, 90')
  const [groupBy, setGroupBy] = useState('region')
  const [filters, setFilters] = useState<WorkforceForecastFilters>(
    emptyWorkforceForecastFilters,
  )
  const [result, setResult] = useState<WorkforceForecastResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isFilterOpen, setIsFilterOpen] = useState(false)
  const [error, setError] = useState('')

  const horizonDays = useMemo(
    () =>
      horizons
        .split(',')
        .map((value) => Number(value.trim()))
        .filter((value) => Number.isFinite(value) && value > 0),
    [horizons],
  )

  const groupByOptions =
    filterOptions?.employeeGroupByOptions?.length
      ? filterOptions.employeeGroupByOptions
      : fallbackGroupByOptions

  const activeFilterCount = Object.values(filters).filter(Boolean).length
  const activeFilterLabels = useMemo(
    () =>
      Object.entries(filters)
        .filter(([, value]) => Boolean(value))
        .map(([name, value]) => `${labelize(name)}: ${value}`),
    [filters],
  )
  const forecastRows = useMemo(() => result?.forecast ?? [], [result])
  const groupedForecastRows = useMemo(
    () => buildGroupedForecastRows(forecastRows),
    [forecastRows],
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
      const response = await analysisApi.workforceForecast({
        asOfDate,
        horizons: horizonDays,
        groupBy,
        filters: compactFilters(filters),
      })
      setResult(response)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to load workforce forecast.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  function updateFilter(name: keyof WorkforceForecastFilters, value: string) {
    setFilters((current) => ({ ...current, [name]: value }))
  }

  function clearFilters() {
    setFilters(emptyWorkforceForecastFilters)
  }

  return (
    <>
      <AnalysisHeader
        title="Workforce Forecast"
        eyebrow="Availability planning"
        metaItems={[
          `${horizonDays.join('/')} days`,
          `Group: ${labelize(groupBy)}`,
          ...activeFilterLabels,
        ]}
      />

      <section className="analysis-command-card" aria-label="Forecast controls">
        <div className="analysis-command-bar analysis-command-bar-forecast">
          <label className="analysis-field">
            <span>As of date</span>
            <input
              type="date"
              value={asOfDate}
              onChange={(event) => setAsOfDate(event.target.value)}
            />
          </label>

          <label className="analysis-skill-input">
            <span>Horizons</span>
            <input
              value={horizons}
              onChange={(event) => setHorizons(event.target.value)}
              placeholder="30, 60, 90"
            />
          </label>

          <button
            className="analysis-icon-button"
            type="button"
            onClick={() => setIsFilterOpen((current) => !current)}
            title="Filters"
            aria-label="Filters"
          >
            <SlidersHorizontal size={17} aria-hidden="true" />
            {activeFilterCount > 0 ? (
              <span className="analysis-filter-count">{activeFilterCount}</span>
            ) : null}
          </button>

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

      {isFilterOpen ? (
        <ForecastFilterPanel
          filterOptions={filterOptions}
          filters={filters}
          groupBy={groupBy}
          groupByOptions={groupByOptions}
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
          onFilterChange={updateFilter}
          onGroupByChange={setGroupBy}
        />
      ) : null}

      {error ? <div className="analysis-error">{error}</div> : null}

      <section
        className="analysis-overview analysis-forecast-overview"
        aria-label="Forecast summary"
      >
        {forecastRows.map((item) => (
          <ForecastKpi
            key={item.horizonDays}
            label={`${item.horizonDays}-day availability`}
            value={item.availableEmployeeCount}
            detail={`${item.totalAvailableFte} FTE - cutoff ${item.cutoffDate}`}
          />
        ))}
      </section>

      <section className="analysis-dashboard-grid">
        <ForecastTrendPanel forecast={forecastRows} />
        <GroupedForecastPanel
          groupBy={groupBy}
          rows={groupedForecastRows}
          horizons={forecastRows.map((item) => item.horizonDays)}
        />
      </section>
    </>
  )
}

function ForecastFilterPanel({
  filterOptions,
  filters,
  groupBy,
  groupByOptions,
  activeFilterCount,
  onClearFilters,
  onFilterChange,
  onGroupByChange,
}: {
  filterOptions: FilterOptions | null
  filters: WorkforceForecastFilters
  groupBy: string
  groupByOptions: string[]
  activeFilterCount: number
  onClearFilters: () => void
  onFilterChange: (name: keyof WorkforceForecastFilters, value: string) => void
  onGroupByChange: (value: string) => void
}) {
  return (
    <section className="analysis-filter-panel" aria-label="Forecast filters">
      <div className="analysis-filter-panel-header">
        <span>Filters</span>
        <button
          className="analysis-clear-button"
          type="button"
          onClick={onClearFilters}
          disabled={activeFilterCount === 0}
        >
          <X size={14} aria-hidden="true" />
          <span>Clear</span>
        </button>
      </div>

      <div className="analysis-filter-grid">
        <SelectFilter
          label="Group by"
          value={groupBy}
          options={groupByOptions}
          formatOption={labelize}
          onChange={onGroupByChange}
        />
        <SelectFilter
          label="Region"
          value={filters.region}
          options={filterOptions?.filters.regions ?? []}
          onChange={(value) => onFilterChange('region', value)}
        />
        <SelectFilter
          label="Domain"
          value={filters.domain}
          options={filterOptions?.filters.domains ?? []}
          onChange={(value) => onFilterChange('domain', value)}
        />
        <SelectFilter
          label="Grade"
          value={filters.grade}
          options={filterOptions?.filters.grades ?? []}
          onChange={(value) => onFilterChange('grade', value)}
        />
        <SelectFilter
          label="Role"
          value={filters.role}
          options={filterOptions?.filters.roles ?? []}
          onChange={(value) => onFilterChange('role', value)}
        />
        <SelectFilter
          label="Availability"
          value={filters.availabilityCategory}
          options={filterOptions?.filters.availabilityCategories ?? []}
          onChange={(value) => onFilterChange('availabilityCategory', value)}
        />
      </div>
    </section>
  )
}

function SelectFilter({
  label,
  value,
  options,
  formatOption = (option: string) => option,
  onChange,
}: {
  label: string
  value: string
  options: string[]
  formatOption?: (option: string) => string
  onChange: (value: string) => void
}) {
  return (
    <label className="analysis-field">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">All</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {formatOption(option)}
          </option>
        ))}
      </select>
    </label>
  )
}

function ForecastKpi({
  label,
  value,
  detail,
}: {
  label: string
  value: string | number
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

function ForecastTrendPanel({
  forecast,
}: {
  forecast: WorkforceForecastBucket[]
}) {
  return (
    <div className="analysis-panel analysis-panel-large">
      <div className="analysis-panel-header">
        <h2>Availability Forecast</h2>
      </div>
      <div className="analysis-chart">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={forecast} margin={{ top: 8, right: 20, bottom: 8, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="horizonDays" tickFormatter={(value) => `${value}d`} />
            <YAxis yAxisId="employees" allowDecimals={false} />
            <YAxis yAxisId="fte" orientation="right" />
            <Tooltip />
            <Legend />
            <Line
              yAxisId="employees"
              type="monotone"
              dataKey="availableEmployeeCount"
              name="Available people"
              stroke="#2563eb"
              strokeWidth={2}
              dot={{ r: 4 }}
            />
            <Line
              yAxisId="fte"
              type="monotone"
              dataKey="totalAvailableFte"
              name="Available FTE"
              stroke="#10b981"
              strokeWidth={2}
              dot={{ r: 4 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function GroupedForecastPanel({
  groupBy,
  rows,
  horizons,
}: {
  groupBy: string
  rows: GroupedForecastRow[]
  horizons: number[]
}) {
  return (
    <div className="analysis-panel">
      <div className="analysis-panel-header">
        <h2>{labelize(groupBy)} Availability</h2>
      </div>
      <div className="analysis-chart analysis-chart-wide">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="groupValue" />
            <YAxis allowDecimals={false} />
            <Tooltip />
            <Legend />
            {horizons.map((horizon, index) => (
              <Bar
                key={horizon}
                dataKey={`${horizon}d`}
                name={`${horizon} days`}
                fill={['#2563eb', '#10b981', '#f59e0b'][index % 3]}
                radius={[5, 5, 0, 0]}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function buildGroupedForecastRows(forecast: WorkforceForecastBucket[]) {
  const rows = new Map<string, GroupedForecastRow>()

  forecast.forEach((bucket) => {
    const groupedAvailability = bucket.groupedAvailability ?? []

    groupedAvailability.forEach((item) => {
      const row = rows.get(item.groupValue) ?? { groupValue: item.groupValue }
      row[`${bucket.horizonDays}d`] = item.availableEmployeeCount
      rows.set(item.groupValue, row)
    })
  })

  return [...rows.values()]
}

export default WorkforceForecastView
