import { Loader2, Sparkles } from 'lucide-react'

function AiGenerationOverlay() {
  return (
    <div className="ai-generation-overlay" role="status" aria-live="polite">
      <div className="ai-generation-panel">
        <div className="ai-generation-icon">
          <Sparkles size={22} />
        </div>
        <div>
          <strong>Generating AI explanation</strong>
          <p>
            The backend is sending the stored recommendation evidence to OpenAI.
            Scores, ranking, and selected members will not change.
          </p>
        </div>
        <Loader2 className="spin" size={22} />
      </div>
    </div>
  )
}

export default AiGenerationOverlay
