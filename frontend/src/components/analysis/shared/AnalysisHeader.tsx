import PageHeader from '../../common/PageHeader'

type AnalysisHeaderProps = {
  eyebrow: string
  metaItems: string[]
  title?: string
}

function AnalysisHeader({ eyebrow, metaItems, title = 'Analysis' }: AnalysisHeaderProps) {
  return (
    <header className="analysis-header">
      <div>
        <PageHeader title={title} />
        <span className="analysis-eyebrow">{eyebrow}</span>
      </div>
      {metaItems.length > 0 ? (
        <div className="analysis-header-meta" aria-label="Current setup">
          {metaItems.map((item) => (
            <span key={item}>{item}</span>
          ))}
        </div>
      ) : null}
    </header>
  )
}

export default AnalysisHeader
