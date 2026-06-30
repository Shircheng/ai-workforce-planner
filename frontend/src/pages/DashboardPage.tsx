import { useRef, useState, type ChangeEvent } from 'react'
import { CheckCircle2, FileSpreadsheet, LoaderCircle, UploadCloud, XCircle } from 'lucide-react'
import { importApi } from '../api/importApi'
import './DashboardPage.css'

type ToastState = {
  type: 'success' | 'error'
  message: string
}

function DashboardPage() {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [selectedFileName, setSelectedFileName] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const [toast, setToast] = useState<ToastState | null>(null)

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    setSelectedFileName(file.name)
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
      </div>

      <div className="dataset-import-panel">
        <div className="dataset-import-copy">
          <div className="dataset-import-icon">
            <FileSpreadsheet />
          </div>
          <div>
            <h2>Dataset Import</h2>
            <p>Choose the workforce Excel file. The upload starts automatically.</p>
          </div>
        </div>

        <label className={`dataset-file-picker ${isUploading ? 'is-uploading' : ''}`}>
          <input
            ref={inputRef}
            type="file"
            accept=".xlsx,.xls"
            disabled={isUploading}
            onChange={handleFileChange}
          />
          <span className="dataset-picker-main">
            {isUploading ? (
              <LoaderCircle className="dataset-spinner" />
            ) : (
              <UploadCloud />
            )}
            {isUploading ? 'Uploading dataset...' : 'Choose Excel file'}
          </span>
          <span className="dataset-picker-sub">
            {selectedFileName || 'Accepted formats: .xlsx, .xls'}
          </span>
        </label>
      </div>

    </section>
  )
}

export default DashboardPage
