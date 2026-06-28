import { X } from 'lucide-react'
import type { FilterOptions } from '../../../api/analysisApi'
import type { SkillGapFilters } from './analysisModel'
import { labelize } from './analysisModel'

type AnalysisFilterPanelProps = {
  filterOptions: FilterOptions | null
  filters: SkillGapFilters
  groupBy: string
  groupByOptions: string[]
  minSkillLevel: number
  activeFilterCount: number
  onClearFilters: () => void
  onFilterChange: (name: keyof SkillGapFilters, value: string) => void
  onGroupByChange: (value: string) => void
  onMinSkillLevelChange: (value: number) => void
}

function AnalysisFilterPanel({
  filterOptions,
  filters,
  groupBy,
  groupByOptions,
  minSkillLevel,
  activeFilterCount,
  onClearFilters,
  onFilterChange,
  onGroupByChange,
  onMinSkillLevelChange,
}: AnalysisFilterPanelProps) {
  return (
    <section className="analysis-filter-panel" aria-label="Advanced filters">
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
        <label className="analysis-field">
          <span>Group by</span>
          <select
            value={groupBy}
            onChange={(event) => onGroupByChange(event.target.value)}
          >
            {groupByOptions.map((option) => (
              <option key={option} value={option}>
                {labelize(option)}
              </option>
            ))}
          </select>
        </label>
        <label className="analysis-field">
          <span>Minimum level</span>
          <select
            value={minSkillLevel}
            onChange={(event) => onMinSkillLevelChange(Number(event.target.value))}
          >
            {[1, 2, 3, 4, 5].map((level) => (
              <option key={level} value={level}>
                {level}
              </option>
            ))}
          </select>
        </label>
        <SelectFilter
          label="Skill category"
          value={filters.skillCategory}
          options={filterOptions?.filters.skillCategories ?? []}
          onChange={(value) => onFilterChange('skillCategory', value)}
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
  onChange,
}: {
  label: string
  value: string
  options: string[]
  onChange: (value: string) => void
}) {
  return (
    <label className="analysis-field">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">All</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  )
}

export default AnalysisFilterPanel
