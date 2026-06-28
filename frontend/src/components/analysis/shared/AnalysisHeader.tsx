type AnalysisHeaderProps = {
  eyebrow: string
  metaItems: string[]
  title?: string
}

function AnalysisHeader({ eyebrow, metaItems, title = 'Analysis' }: AnalysisHeaderProps) {
  return (
    <header className="analysis-header">
      <div>
        <h1>{title}</h1>
        <span className="analysis-eyebrow">{eyebrow}</span>
      </div>
      <div className="analysis-header-meta" aria-label="Current setup">
        {metaItems.map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>
    </header>
  )
}

export default AnalysisHeader
