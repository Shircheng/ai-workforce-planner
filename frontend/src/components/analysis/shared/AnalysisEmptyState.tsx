import { UploadCloud } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

type AnalysisEmptyStateProps = {
  title?: string
  message?: string
}

function AnalysisEmptyState({
  title = 'No analysis data yet',
  message = 'Import your workforce Excel dataset from the Dashboard, then run the analysis again.',
}: AnalysisEmptyStateProps) {
  const navigate = useNavigate()

  return (
    <section className="analysis-empty-state">
      <UploadCloud aria-hidden="true" />
      <h2>{title}</h2>
      <p>{message}</p>
      <button className="analysis-empty-state-button" type="button" onClick={() => navigate('/')}>
        Go to Dashboard
      </button>
    </section>
  )
}

export default AnalysisEmptyState
