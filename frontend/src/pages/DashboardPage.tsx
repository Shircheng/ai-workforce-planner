import { useRef, useState, type ChangeEvent } from 'react'
import { CheckCircle2, LoaderCircle, UploadCloud, XCircle } from 'lucide-react'
import { importApi } from '../api/importApi'
import './DashboardPage.css'

type ToastState = {
  type: 'success' | 'error'
  message: string
}

function DashboardPage() {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const [toast, setToast] = useState<ToastState | null>(null)

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    setIsUploading(true)
    setToast(null)

    try {
      await importApi.uploadWorkforceDataset(file)
      setToast({
        type: 'success',
        message: 'Dataset imported successfully.',
      })
    } catch (error) {
      setToast({
        type: 'error',
        message: error instanceof Error ? error.message : 'Dataset import failed.',
      })
    } finally {
      setIsUploading(false)
      event.target.value = ''
    }
  }

  return (
    <section className="dashboard-page">
      {toast ? (
        <div className={`dashboard-toast dashboard-toast-${toast.type}`}>
          {toast.type === 'success' ? (
            <CheckCircle2 className="dashboard-toast-icon" />
          ) : (
            <XCircle className="dashboard-toast-icon" />
          )}
          <span>{toast.message}</span>
          <button
            type="button"
            onClick={() => setToast(null)}
            aria-label="Dismiss message"
          >
            x
          </button>
        </div>
      ) : null}

      <div className="dashboard-header">
        <div>
          <h1>Workforce Dashboard</h1>
          <p>Load the workforce Excel dataset before exploring talent, analysis, and EWA flows.</p>
        </div>
        <div className="dashboard-actions">
          <input
            ref={inputRef}
            className="dataset-file-input"
            type="file"
            accept=".xlsx,.xls"
            disabled={isUploading}
            onChange={handleFileChange}
          />
          <button
            type="button"
            className="dataset-import-button"
            disabled={isUploading}
            onClick={() => inputRef.current?.click()}
          >
            {isUploading ? (
              <LoaderCircle className="dataset-spinner" />
            ) : (
              <UploadCloud />
            )}
            {isUploading ? 'Importing...' : 'Import Dataset'}
          </button>
        </div>
      </div>

    </section>
  )
}

export default DashboardPage
