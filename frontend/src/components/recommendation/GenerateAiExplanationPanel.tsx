import { Loader2, Sparkles } from 'lucide-react'

type GenerateAiExplanationPanelProps = {
  compact?: boolean
  disabled: boolean
  onGenerate: () => void
}

function GenerateAiExplanationPanel({
  compact = false,
  disabled,
  onGenerate,
}: GenerateAiExplanationPanelProps) {
  return (
    <div className={`generate-ai-panel ${compact ? 'compact' : ''}`}>
      <div className="generate-ai-icon">
        <Sparkles size={compact ? 16 : 18} />
      </div>
      <div className="generate-ai-copy">
        <strong>AI explanation not generated yet</strong>
        <p>
          Generate AI explanations from the stored recommendation run. Backend
          scores and ranking will not change.
        </p>
      </div>
      <button
        className="secondary-button ai-explanation-button"
        disabled={disabled}
        onClick={onGenerate}
        type="button"
      >
        {disabled ? <Loader2 className="spin" size={16} /> : <Sparkles size={16} />}
        Generate AI Explanation
      </button>
    </div>
  )
}

export default GenerateAiExplanationPanel
