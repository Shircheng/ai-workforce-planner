import { UploadCloud } from 'lucide-react'

type AnalysisEmptyStateProps = {
  title?: string
  message?: string
}

function AnalysisEmptyState({
  title = 'No analysis data yet',
  message = 'Import your workforce Excel dataset from the Dashboard, then run the analysis again.',
}: AnalysisEmptyStateProps) {
  return (
    <section className="analysis-empty-state">
      <UploadCloud aria-hidden="true" />
      <h2>{title}</h2>
      <p>{message}</p>
    </section>
  )
}

export default AnalysisEmptyState
