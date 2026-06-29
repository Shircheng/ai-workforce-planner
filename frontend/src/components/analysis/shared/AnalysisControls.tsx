import { RefreshCw, SlidersHorizontal } from 'lucide-react'

type AnalysisControlsProps = {
  requiredSkills: string
  activeFilterCount: number
  isLoading: boolean
  onRequiredSkillsChange: (value: string) => void
  onToggleFilters: () => void
  onRun: () => void
}

function AnalysisControls({
  requiredSkills,
  activeFilterCount,
  isLoading,
  onRequiredSkillsChange,
  onToggleFilters,
  onRun,
}: AnalysisControlsProps) {
  return (
    <section className="analysis-command-card" aria-label="Skill gap controls">
      <div className="analysis-command-bar">
        <label className="analysis-skill-input">
          <span>Required skills</span>
          <input
            value={requiredSkills}
            onChange={(event) => onRequiredSkillsChange(event.target.value)}
            placeholder="React, Java, AWS"
          />
        </label>

        <button
          className="analysis-icon-button"
          type="button"
          onClick={onToggleFilters}
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
          onClick={onRun}
          disabled={isLoading}
        >
          <RefreshCw size={16} aria-hidden="true" />
          <span>{isLoading ? 'Running' : 'Run'}</span>
        </button>
      </div>
    </section>
  )
}

export default AnalysisControls
