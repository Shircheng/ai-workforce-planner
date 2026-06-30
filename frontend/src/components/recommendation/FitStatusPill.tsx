function FitStatusPill({ status }: { status?: string }) {
  const normalized = status?.trim()
  const lower = normalized?.toLowerCase() ?? ''
  const tone = lower.includes('risk') || lower.includes('backup')
    ? 'warn'
    : lower.includes('stretch')
      ? 'medium'
      : 'strong'

  return (
    <span className={`fit-status-pill ${tone}`}>
      {normalized || 'Fit status unavailable'}
    </span>
  )
}

export default FitStatusPill
