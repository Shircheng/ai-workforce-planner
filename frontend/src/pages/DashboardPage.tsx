import { useRef, useMemo, useState, useEffect, type ChangeEvent, type ReactNode } from 'react'
import {
  AlertTriangle,
  BarChart3,
  BriefcaseBusiness,
  CheckCircle2,
  Globe2,
  LoaderCircle,
  UploadCloud,
  UsersRound,
  XCircle,
} from 'lucide-react'
import { importApi } from '../api/importApi'
import './DashboardPage.css'
import PageHeader from '../components/common/PageHeader'
import {
  employeeApi,
  type DashboardAlert,
  type DashboardBar,
  type DashboardMetric,
  type WorkforceDashboardResponse,
} from '../api/employeeApi'

type ToastState = {
  type: 'success' | 'error'
  message: string
}


const chartColors = ['bg-teal-700', 'bg-[#2563eb]', 'bg-[#9a3412]', 'bg-slate-700']

const metricIcons = [UsersRound, CheckCircle2, BarChart3, BriefcaseBusiness]

function DashboardPage() {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const [toast, setToast] = useState<ToastState | null>(null)
  const [dashboard, setDashboard] = useState<WorkforceDashboardResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    setIsUploading(true)
    setToast(null)

    try {
      await importApi.uploadWorkforceDataset(file)
      setDashboard(await fetchDashboard())
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

  useEffect(() => {
    let isCurrentRequest = true

    setIsLoading(true)
    void fetchDashboard()
      .then((response) => {
        if (isCurrentRequest) {
          setDashboard(response)
        }
      })
      .finally(() => {
        if (isCurrentRequest) {
          setIsLoading(false)
        }
      })

    return () => {
      isCurrentRequest = false
    }
  }, [])

  const hasData = hasDashboardData(dashboard)

  const maxAvailability = useMemo(
    () => maxBarValue(dashboard?.availabilityOutlook ?? []),
    [dashboard],
  )

  const toastMessage = toast ? (
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
  ) : null

  const importControl = (
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
  )

  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-300 bg-white p-8 text-center text-base font-semibold text-slate-500">
        Loading workforce dashboard...
      </div>
    )
  }

  if (!hasData || !dashboard) {
    return (
      <section className="dashboard-page">
        {toastMessage}
        <div className="dashboard-header">
          <div>
            <PageHeader title="Workforce Dashboard" />
            <p>
              Import your workforce Excel dataset to populate dashboard metrics,
              availability, supply, skills, and planning alerts.
            </p>
          </div>
          {importControl}
        </div>
        <section className="dashboard-empty-reminder">
          <UploadCloud />
          <h2>No dashboard data yet</h2>
          <p>
            Use the Import Dataset button to upload data. The dashboard will
            refresh automatically after the import completes.
          </p>
        </section>
      </section>
    )
  }

  return (
    <section className="dashboard-page text-slate-950">
      {toastMessage}

      <div className="dashboard-header">
        <div>
          <PageHeader title="Workforce Dashboard" />
          <p>Load the workforce Excel dataset before exploring talent, analysis, and EWA flows.</p>
        </div>
        {importControl}
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {dashboard.metrics.map((metric, index) => (
          <MetricCard key={metric.label} metric={metric} index={index} />
        ))}
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-[2fr_1fr]">
        <section className="rounded-lg border border-slate-300 bg-white p-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-xl font-bold text-slate-950">
                Availability Outlook
              </h2>
              <p className="mt-1 text-base text-slate-500">
                People available by release window
              </p>
            </div>
            <BarChart3 className="h-6 w-6 text-[#2563eb]" />
          </div>

          <div className="mt-6 space-y-4">
            {dashboard.availabilityOutlook.map((item, index) => (
              <HorizontalBar
                key={item.label}
                item={item}
                maxValue={maxAvailability}
                colorClass={chartColors[index % chartColors.length]}
              />
            ))}
          </div>
        </section>

        <section className="rounded-lg border border-slate-300 bg-white p-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-xl font-bold text-slate-950">
                Planning Alerts
              </h2>
              <p className="mt-1 text-base text-slate-500">
                Signals generated from demand and supply
              </p>
            </div>
            <AlertTriangle className="h-6 w-6 text-[#9a3412]" />
          </div>

          <div className="mt-5 space-y-3">
            {dashboard.alerts.map((alert) => (
              <AlertCard key={alert.title} alert={alert} />
            ))}
          </div>
        </section>
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-3">
        <CompactBarCard
          title="Supply By Role"
          subtitle="Largest role pools"
          items={dashboard.supplyByRole}
        />
        <SkillCard items={dashboard.topSkills} />
        <CompactBarCard
          title="Regions"
          subtitle="People by workforce region"
          items={dashboard.regions}
          icon={<Globe2 className="h-5 w-5 text-[#2563eb]" />}
        />
      </div>

      <div className="mt-5 grid gap-5 lg:grid-cols-[1.1fr_1fr]">
        <CompactBarCard
          title="Demand By Domain"
          subtitle="Open opportunity concentration"
          items={dashboard.demandByDomain}
        />
        <section className="rounded-lg border border-slate-300 bg-white p-5">
          <h2 className="text-xl font-bold text-slate-950">
            Executive Readout
          </h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-3">
            <Readout label="Largest Supply" value={dashboard.supplyByRole[0]?.label ?? 'N/A'} />
            <Readout label="Top Skill" value={dashboard.topSkills[0]?.label ?? 'N/A'} />
            <Readout label="Top Demand" value={dashboard.demandByDomain[0]?.label ?? 'N/A'} />
          </div>
        </section>
      </div>
    </section>
  )
}

function MetricCard({ metric, index }: { metric: DashboardMetric; index: number }) {
  const Icon = metricIcons[index % metricIcons.length]

  return (
    <div className="rounded-lg border border-slate-300 bg-white p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-base font-medium text-slate-500">{metric.label}</div>
          <div className="mt-2 text-3xl font-bold text-slate-950">
            {metric.value.toLocaleString()}
          </div>
        </div>
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-[#2563eb]">
          <Icon className="h-5 w-5" />
        </div>
      </div>
      <div className="mt-3 inline-flex rounded-full bg-teal-50 px-3 py-1 text-sm font-medium text-teal-700">
        {metric.note}
      </div>
    </div>
  )
}

function HorizontalBar({
  item,
  maxValue,
  colorClass,
}: {
  item: DashboardBar
  maxValue: number
  colorClass: string
}) {
  const width = maxValue === 0 ? 0 : Math.max((item.value / maxValue) * 100, 4)

  return (
    <div className="grid grid-cols-[minmax(92px,0.35fr)_1fr_64px] items-center gap-3">
      <div className="text-base text-slate-700">{item.label}</div>
      <div className="h-3 overflow-hidden rounded-full bg-slate-100">
        <div className={`h-full rounded-full ${colorClass}`} style={{ width: `${width}%` }} />
      </div>
      <div className="text-right text-base font-bold text-slate-950">
        {item.value.toLocaleString()}
      </div>
    </div>
  )
}

function CompactBarCard({
  title,
  subtitle,
  items,
  icon,
}: {
  title: string
  subtitle: string
  items: DashboardBar[]
  icon?: ReactNode
}) {
  const maxValue = maxBarValue(items)

  return (
    <section className="rounded-lg border border-slate-300 bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-950">{title}</h2>
          <p className="mt-1 text-base text-slate-500">{subtitle}</p>
        </div>
        {icon}
      </div>
      <div className="mt-5 space-y-3">
        {items.length === 0 ? (
          <div className="rounded-lg bg-slate-50 px-4 py-6 text-center text-base font-medium text-slate-500">
            No data available.
          </div>
        ) : (
          items.map((item, index) => (
            <HorizontalBar
              key={item.label}
              item={item}
              maxValue={maxValue}
              colorClass={chartColors[index % chartColors.length]}
            />
          ))
        )}
      </div>
    </section>
  )
}

function SkillCard({ items }: { items: DashboardBar[] }) {
  return (
    <section className="rounded-lg border border-slate-300 bg-white p-5">
      <h2 className="text-xl font-bold text-slate-950">Top Available Skills</h2>
      <p className="mt-1 text-base text-slate-500">
        Most common skills across employee profiles
      </p>
      <div className="mt-5 flex flex-wrap gap-2">
        {items.length === 0 ? (
          <div className="rounded-lg bg-slate-50 px-4 py-6 text-base font-medium text-slate-500">
            No skills available.
          </div>
        ) : (
          items.map((item, index) => (
            <span
              key={item.label}
              className={`rounded-full px-3 py-1 text-base font-medium ${
                index === 0
                  ? 'bg-teal-50 text-teal-700'
                  : 'bg-slate-100 text-slate-700'
              }`}
            >
              {item.label} {item.value}
            </span>
          ))
        )}
      </div>
    </section>
  )
}

function AlertCard({ alert }: { alert: DashboardAlert }) {
  const toneClass =
    alert.tone === 'success'
      ? 'border-l-[#166534] bg-green-50'
      : alert.tone === 'warning'
        ? 'border-l-[#9a3412] bg-orange-50'
        : 'border-l-teal-700 bg-teal-50'

  return (
    <div className={`rounded-lg border-l-4 p-4 ${toneClass}`}>
      <div className="text-base font-bold text-slate-950">{alert.title}</div>
      <p className="mt-2 text-base leading-6 text-slate-600">{alert.message}</p>
    </div>
  )
}

function Readout({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 p-4">
      <div className="text-sm font-semibold uppercase text-slate-500">
        {label}
      </div>
      <div className="mt-2 text-lg font-bold text-slate-950">{value}</div>
    </div>
  )
}

function maxBarValue(items: DashboardBar[]) {
  return Math.max(...items.map((item) => item.value), 0)
}

async function fetchDashboard() {
  try {
    return await employeeApi.getDashboard()
  } catch {
    return null
  }
}

function hasDashboardData(dashboard: WorkforceDashboardResponse | null) {
  if (!dashboard) {
    return false
  }

  return [
    dashboard.metrics,
    dashboard.availabilityOutlook,
    dashboard.supplyByRole,
    dashboard.topSkills,
    dashboard.regions,
    dashboard.demandByDomain,
    dashboard.alerts,
  ].some((items) => items.length > 0)
}

export default DashboardPage
