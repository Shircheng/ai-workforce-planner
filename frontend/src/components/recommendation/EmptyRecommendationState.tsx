import { ClipboardPlus, Loader2, Users } from 'lucide-react'

type EmptyRecommendationStateProps = {
  isGenerating: boolean
  needsOpportunitySetup?: boolean
  onCreateOpportunity: () => void
  onGenerate: () => void
  opportunityName?: string
}

function EmptyRecommendationState({
  isGenerating,
  needsOpportunitySetup = false,
  onCreateOpportunity,
  onGenerate,
  opportunityName,
}: EmptyRecommendationStateProps) {
  if (needsOpportunitySetup) {
    return (
      <div className="empty-recommendations">
        <div className="empty-icon">
          <ClipboardPlus size={24} />
        </div>
        <h2>Create an opportunity first</h2>
        <p>
          Recommendation options need an opportunity and parsed role requirements
          from Opportunity Intake. Create and save the opportunity there, then
          generate options for the recommender.
        </p>
        <button
          className="primary-button"
          onClick={onCreateOpportunity}
          type="button"
        >
          <ClipboardPlus size={16} />
          Create Opportunity
        </button>
      </div>
    )
  }

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
