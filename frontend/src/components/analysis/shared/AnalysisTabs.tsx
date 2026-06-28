export type AnalysisTab = 'skill-gap' | 'workforce-forecast' | 'opportunity-forecast'

type AnalysisTabsProps = {
  activeTab: AnalysisTab
  onChange: (tab: AnalysisTab) => void
}

const tabs: Array<{ id: AnalysisTab; label: string }> = [
  { id: 'skill-gap', label: 'Skill Gap' },
  { id: 'workforce-forecast', label: 'Workforce Forecast' },
  { id: 'opportunity-forecast', label: 'Opportunity Forecast' },
]

function AnalysisTabs({ activeTab, onChange }: AnalysisTabsProps) {
  return (
    <div className="analysis-tabs" role="tablist" aria-label="Analysis views">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={activeTab === tab.id}
          className={
            activeTab === tab.id
              ? 'analysis-tab-button active'
              : 'analysis-tab-button'
          }
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}

export default AnalysisTabs
