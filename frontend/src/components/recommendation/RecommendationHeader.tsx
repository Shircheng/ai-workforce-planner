import { FileCheck2, Layers3 } from 'lucide-react'
import type { RefObject } from 'react'
import type { RecommendationOption, RecommendationRun } from '../../types/Recommendation'

type RecommendationHeaderProps = {
  comparisonRef: RefObject<HTMLDivElement | null>
  recommendationRun: RecommendationRun | null
  selectedOption: RecommendationOption | null
  onPrepareEwaPack: () => void
}

function RecommendationHeader({
  comparisonRef,
  recommendationRun,
  selectedOption,
  onPrepareEwaPack,
}: RecommendationHeaderProps) {
  return (
    <div className="recommendation-topbar">
      <div>
        <h1>Recommendations</h1>
        <p className="recommendation-subtitle">
          Evidence-backed staffing options generated from skill fit,
          availability, grade, location, domain, and project evidence.
        </p>
      </div>
      <div className="recommendation-actions">
        <button
          className="secondary-button"
          disabled={!recommendationRun}
          onClick={() =>
            comparisonRef.current?.scrollIntoView({ behavior: 'smooth' })
          }
          type="button"
        >
          <Layers3 size={16} />
          Compare Teams
        </button>
        <button
          className="primary-button"
          disabled={!selectedOption}
          onClick={onPrepareEwaPack}
          type="button"
        >
          <FileCheck2 size={16} />
          Prepare EWA Pack
        </button>
      </div>
    </div>
  )
}

export default RecommendationHeader
