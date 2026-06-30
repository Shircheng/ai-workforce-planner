import { Loader2, Users } from 'lucide-react'

type EmptyRecommendationStateProps = {
  isGenerating: boolean
  onGenerate: () => void
  opportunityName?: string
}

function EmptyRecommendationState({
  isGenerating,
  onGenerate,
  opportunityName,
}: EmptyRecommendationStateProps) {
  return (
    <div className="empty-recommendations">
      <div className="empty-icon">
        <Users size={24} />
      </div>
      <h2>No recommendation run yet</h2>
      <p>
        Generate backend recommendations for {opportunityName ?? 'this opportunity'}.
        The scoring engine will create stored recommendation options, and this
        page will display the stored run without recalculating scores.
      </p>
      <button
        className="primary-button"
        disabled={isGenerating}
        onClick={onGenerate}
        type="button"
      >
        {isGenerating ? <Loader2 className="spin" size={16} /> : <Users size={16} />}
        Generate Recommendations
      </button>
    </div>
  )
}

export default EmptyRecommendationState
