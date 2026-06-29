type EwaStatusPanelProps = {
  status: string
  generatedAt?: string
  notes: string
  isSubmitting: boolean
  canSubmit: boolean
  onExportPdf: () => void
  onSubmitToEwa: () => void
  onNotesChange: (value: string) => void
}

function EwaStatusPanel({
  status,
  generatedAt,
  notes,
  isSubmitting,
  canSubmit,
  onExportPdf,
  onSubmitToEwa,
  onNotesChange,
}: EwaStatusPanelProps) {
  const isSubmitted = status === 'SUBMITTED_TO_EWA'

  return (
    <aside className="ewa-card ewa-status-panel">
      <span className="ewa-label">Review status</span>
      <strong>{formatStatus(status)}</strong>
      <small>{generatedAt ? `Generated ${formatDateTime(generatedAt)}` : 'Ready for review'}</small>

      <label className="ewa-notes-input">
        <span>Regional planner notes</span>
        <textarea
          value={notes}
          onChange={(event) => onNotesChange(event.target.value)}
          placeholder="Add notes before submitting to EWA"
        />
      </label>

      <div className="ewa-action-stack">
        <button className="ewa-secondary-button" type="button" onClick={onExportPdf}>
          Export PDF
        </button>
        <button
          className="ewa-primary-button"
          type="button"
          onClick={onSubmitToEwa}
          disabled={isSubmitted || isSubmitting || !canSubmit}
        >
          {isSubmitting ? 'Submitting' : isSubmitted ? 'Submitted' : 'Submit to EWA'}
        </button>
      </div>
    </aside>
  )
}

function formatStatus(status: string) {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default EwaStatusPanel
