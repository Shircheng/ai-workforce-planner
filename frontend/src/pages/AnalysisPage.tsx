import { useEffect, useState } from 'react'
import '../components/analysis/Analysis.css'
import {
  AnalysisTabs,
  type AnalysisTab,
  OpportunityForecastView,
  SkillGapView,
  WorkforceForecastView,
} from '../components/analysis'
import { analysisApi, type FilterOptions } from '../api/analysisApi'

function AnalysisPage() {
  const [activeTab, setActiveTab] = useState<AnalysisTab>('skill-gap')
  const [filterOptions, setFilterOptions] = useState<FilterOptions | null>(null)

  useEffect(() => {
    let isMounted = true

    analysisApi
      .filterOptions()
      .then((options) => {
        if (isMounted) setFilterOptions(options)
      })
      .catch(() => {
        if (isMounted) setFilterOptions(null)
      })

    return () => {
      isMounted = false
    }
  }, [])

  return (
    <div className="analysis-page">
      <AnalysisTabs activeTab={activeTab} onChange={setActiveTab} />

      {activeTab === 'skill-gap' ? (
        <SkillGapView filterOptions={filterOptions} />
      ) : activeTab === 'workforce-forecast' ? (
        <WorkforceForecastView filterOptions={filterOptions} />
      ) : (
        <OpportunityForecastView />
      )}
    </div>
  )
}

export default AnalysisPage
