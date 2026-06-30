function RiskBadge({ riskLevel }: { riskLevel?: string }) {
  const normalized = (riskLevel ?? '').toLowerCase()
  const tone = normalized.includes('low')
    ? 'low'
    : normalized.includes('high')
      ? 'high'
      : 'medium'

  return <span className={`risk-badge ${tone}`}>{riskLevel ?? 'Risk n/a'}</span>
}

export default RiskBadge
