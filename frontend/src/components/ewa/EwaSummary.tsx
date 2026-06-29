import type { EwaOpportunitySummary, EwaTeamOptionSummary } from '../../api/ewaApi'

type EwaSummaryProps = {
  opportunity?: EwaOpportunitySummary
  teamOption?: EwaTeamOptionSummary
}

function EwaSummary({ opportunity, teamOption }: EwaSummaryProps) {
  return (
    <section className="ewa-summary-grid" aria-label="EWA review summary">
      <SummaryCard
        title="Opportunity Summary"
        items={[
          ['Project', opportunity?.opportunityName],
          ['Client', opportunity?.clientName],
          ['Domain', opportunity?.domain],
          ['Location', compactLocation(opportunity)],
          ['Start - End date', formatDateRange(opportunity)],
          ['Duration', opportunity?.durationWeeks ? `${opportunity.durationWeeks} weeks` : undefined],
        ]}
      />

      <SummaryCard
        title="Selected Team Option"
        items={[
          ['Option', teamOption?.optionName],
          ['Match score', formatPercent(teamOption?.matchScore)],
          ['Confidence', formatPercent(teamOption?.confidence)],
          ['Risk level', teamOption?.riskLevel],
          ['Team size', teamOption?.teamSize?.toString()],
        ]}
      />
    </section>
  )
}

function SummaryCard({
  title,
  items,
}: {
  title: string
  items: Array<[string, string | undefined]>
}) {
  return (
    <article className="ewa-card">
      <h2>{title}</h2>
      <dl className="ewa-detail-list">
        {items.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value || '-'}</dd>
          </div>
        ))}
      </dl>
    </article>
  )
}

function compactLocation(opportunity?: EwaOpportunitySummary) {
  return [opportunity?.city, opportunity?.country, opportunity?.region]
    .filter(Boolean)
    .join(', ')
}

function formatDateRange(opportunity?: EwaOpportunitySummary) {
  if (!opportunity?.expectedStartDate) return undefined
  const startDate = opportunity.expectedStartDate
  const endDate = calculateEndDate(startDate, opportunity.durationWeeks)
  return endDate ? `${startDate} - ${endDate}` : startDate
}

function calculateEndDate(startDateValue: string, durationWeeks?: number) {
  if (!durationWeeks) return undefined
  const startDate = new Date(`${startDateValue}T00:00:00`)
  if (Number.isNaN(startDate.getTime())) return undefined
  const endDate = new Date(startDate)
  endDate.setDate(startDate.getDate() + durationWeeks * 7)
  return endDate.toISOString().slice(0, 10)
}

function formatPercent(value?: number) {
  if (value === undefined || value === null) return undefined
  return `${value}%`
}

export default EwaSummary
