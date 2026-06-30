import { Loader2, Sparkles } from 'lucide-react'

type AiExplanationCtaProps = {
  disabled: boolean
  onGenerate: () => void
}

function AiExplanationCta({ disabled, onGenerate }: AiExplanationCtaProps) {
  return (
    <div className="ai-explanation-cta">
      <div className="ai-explanation-cta-icon">
        <Sparkles size={18} />
      </div>
      <div>
        <strong>AI explanation not generated yet</strong>
        <p>
          Generate team and member explanations from this stored recommendation
          run. Backend scores and ranking will not change.
        </p>
      </div>
      <button
        className="secondary-button ai-explanation-button"
        disabled={disabled}
        onClick={onGenerate}
        type="button"
      >
        {disabled ? (
          <Loader2 className="spin" size={16} />
        ) : (
          <Sparkles size={16} />
        )}
        Generate AI Explanation
      </button>
    </div>
  )
}

export default AiExplanationCta
