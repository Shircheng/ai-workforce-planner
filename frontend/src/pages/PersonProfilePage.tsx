import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, CalendarDays, MapPin, UserRound } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import {
  employeeApi,
  type PersonProfileResponse,
  type PersonProjectEvidence,
} from '../api/employeeApi'

const skillChipClass = 'bg-teal-50 text-teal-700'

function formatDate(value: string | null) {
  if (!value) {
    return 'Not set'
  }
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value))
}

function formatNumber(value: number | null) {
  if (value === null || value === undefined) {
    return 'N/A'
  }
  return Number(value).toFixed(2).replace(/\.?0+$/, '')
}

function joinedList(values: string[]) {
  return values.length > 0 ? values.join(', ') : 'No evidence recorded'
}

function PersonProfilePage() {
  const { id } = useParams()
  const [profile, setProfile] = useState<PersonProfileResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) {
      setError('Missing person id')
      setIsLoading(false)
      return
    }

    let isCurrentRequest = true
    setIsLoading(true)
    setError(null)

    employeeApi
      .getPersonProfile(id)
      .then((response) => {
        if (isCurrentRequest) {
          setProfile(response)
        }
      })
      .catch((caughtError: unknown) => {
        if (isCurrentRequest) {
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : 'Unable to load person profile',
          )
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
  }, [id])

  const primarySkills = useMemo(
    () => profile?.skills.slice(0, 6) ?? [],
    [profile],
  )
  const summary = profile?.summary

  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-300 bg-white p-8 text-center text-base font-semibold text-slate-500">
        Loading person profile...
      </div>
    )
  }

  if (error || !profile || !summary) {
    return (
      <div className="rounded-lg border border-slate-300 bg-white p-8">
        <div className="text-base font-semibold text-[#9a3412]">
          {error ?? 'Person profile not found.'}
        </div>
        <Link
          to="/talent"
          className="mt-5 inline-flex h-10 items-center rounded-lg bg-[#2563eb] px-4 text-sm font-extrabold text-white"
        >
          Back to Talent Explorer
        </Link>
      </div>
    )
  }

  return (
    <section className="text-slate-950">
      <Link
        to="/talent"
        className="mb-4 inline-flex items-center gap-2 text-sm font-extrabold text-[#2563eb]"
      >
        <ArrowLeft className="h-4 w-4" />
        Talent Explorer
      </Link>

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="text-[34px] font-extrabold leading-tight tracking-normal text-slate-950">
            Person Profile
          </h1>
          <p className="mt-2 text-[18px] leading-7 text-slate-500">
            Dataset evidence used to understand this candidate.
          </p>
        </div>
      </div>

      <div className="mt-7 rounded-lg border border-slate-300 bg-white p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex items-center gap-4">
            <div className="flex h-[72px] w-[72px] shrink-0 items-center justify-center rounded-full bg-teal-50 text-xl font-extrabold text-teal-700">
              {summary.initials}
            </div>
            <div>
              <div className="text-2xl font-extrabold text-slate-950">
                {summary.name}
              </div>
              <div className="mt-1 text-base text-slate-500">
                {summary.grade || 'Grade not set'} | {summary.role || 'Role not set'} | {summary.location || 'Location not set'}
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <span className="rounded-full bg-green-50 px-3 py-1 text-sm font-semibold text-[#166534]">
                  {summary.availability}
                </span>
                {summary.workMode ? (
                  <span className="rounded-full bg-blue-50 px-3 py-1 text-sm font-semibold text-blue-700">
                    {summary.workMode}
                  </span>
                ) : null}
                {summary.ewaStatus ? (
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-semibold text-slate-700">
                    EWA: {summary.ewaStatus}
                  </span>
                ) : null}
              </div>
            </div>
          </div>

          <div className="grid min-w-full gap-3 sm:grid-cols-3 lg:min-w-[520px]">
            <MetricCard label="Current FTE" value={formatNumber(summary.currentAllocationFTE)} />
            <MetricCard label="Available FTE" value={formatNumber(summary.availableFTECurrent)} />
            <MetricCard label="Project End" value={formatDate(summary.currentProjectEnd)} />
          </div>
        </div>

        <div className="mt-6 grid gap-5 lg:grid-cols-3">
          <InfoPanel title="Availability">
            <div className="flex items-center gap-2 text-base font-semibold text-slate-950">
              <CalendarDays className="h-5 w-5 text-[#2563eb]" />
              {summary.availability}
            </div>
            <p className="mt-4 text-sm leading-6 text-slate-500">
              Current project: {summary.currentProject || 'No active project recorded'}.
              Planned end: {formatDate(summary.currentProjectEnd)}.
            </p>
          </InfoPanel>

          <InfoPanel title="Primary Skills">
            <div className="flex flex-wrap gap-2">
              {primarySkills.length > 0 ? (
                primarySkills.map((skill) => (
                  <span
                    key={skill.skillName}
                    className={`${skillChipClass} rounded-full px-3 py-1 text-sm font-semibold`}
                  >
                    {skill.skillName}
                  </span>
                ))
              ) : (
                <span className="text-sm text-slate-500">No skills recorded.</span>
              )}
            </div>
          </InfoPanel>

          <InfoPanel title="Location And Domains">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-700">
              <MapPin className="h-4 w-4 text-[#2563eb]" />
              {summary.city || 'City not set'}, {summary.country || 'country not set'}
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              {profile.domains.slice(0, 8).map((domain) => (
                <span
                  key={domain}
                  className="rounded-full bg-blue-50 px-3 py-1 text-sm font-semibold text-blue-700"
                >
                  {domain}
                </span>
              ))}
            </div>
          </InfoPanel>
        </div>

        {profile.profile.profileSummary ? (
          <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-5">
            <div className="flex items-center gap-2 text-base font-extrabold text-slate-950">
              <UserRound className="h-5 w-5 text-[#2563eb]" />
              Profile Summary
            </div>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              {profile.profile.profileSummary}
            </p>
          </div>
        ) : null}
      </div>

      <div className="mt-6 space-y-6">
        <EvidenceTable projects={profile.projectEvidence} />
        <AllocationTable allocations={profile.allocations} />

        <div className="grid gap-6 xl:grid-cols-3">
          <SideList title="Strengths" items={profile.profile.keyStrengths} />
          <SideList title="Certifications" items={profile.profile.certifications} />
          <SideList title="Recent Highlights" items={profile.profile.recentHighlights} />
        </div>

        <AvailabilityPanel items={profile.availabilityForecast.slice(0, 8)} />
      </div>
    </section>
  )
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
      <div className="text-xs font-extrabold uppercase text-slate-500">
        {label}
      </div>
      <div className="mt-2 text-lg font-extrabold text-slate-950">{value}</div>
    </div>
  )
}

function InfoPanel({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}) {
  return (
    <div className="rounded-lg border border-slate-300 bg-white p-5">
      <div className="text-lg font-extrabold text-slate-950">{title}</div>
      <div className="mt-4">{children}</div>
    </div>
  )
}

function EvidenceTable({ projects }: { projects: PersonProjectEvidence[] }) {
  return (
    <div className="rounded-lg border border-slate-300 bg-white p-5">
      <h2 className="text-2xl font-extrabold text-slate-950">Project Evidence</h2>
      <div className="mt-5 overflow-x-auto">
        <table className="w-full min-w-[920px] border-collapse text-left">
          <thead className="bg-slate-50">
            <tr className="border-b border-slate-300">
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Project</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Client Domain</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Role</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Evidence</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Dates</th>
            </tr>
          </thead>
          <tbody>
            {projects.length === 0 ? (
              <tr>
                <td className="px-3 py-8 text-center text-sm font-semibold text-slate-500" colSpan={5}>
                  No project evidence recorded.
                </td>
              </tr>
            ) : (
              projects.map((project) => (
                <tr key={`${project.projectName}-${project.startDate}`} className="border-b border-slate-200">
                  <td className="px-3 py-4 text-sm font-semibold text-slate-950">{project.projectName}</td>
                  <td className="px-3 py-4 text-sm text-slate-700">{project.domain || project.clientType}</td>
                  <td className="px-3 py-4 text-sm text-slate-700">{project.role}</td>
                  <td className="px-3 py-4 text-sm leading-6 text-slate-700">
                    {joinedList([
                      ...project.keyTechnologiesOrMethods,
                      ...project.outcomeEvidence,
                    ])}
                  </td>
                  <td className="px-3 py-4 text-sm text-slate-500">
                    {formatDate(project.startDate)} to {formatDate(project.endDate)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function AllocationTable({
  allocations,
}: {
  allocations: PersonProfileResponse['allocations']
}) {
  return (
    <div className="rounded-lg border border-slate-300 bg-white p-5">
      <h2 className="text-2xl font-extrabold text-slate-950">Allocation History</h2>
      <div className="mt-5 overflow-x-auto">
        <table className="w-full min-w-[760px] border-collapse text-left">
          <thead className="bg-slate-50">
            <tr className="border-b border-slate-300">
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Project</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Client</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Role</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">FTE</th>
              <th className="px-3 py-4 text-sm font-extrabold text-slate-700">Status</th>
            </tr>
          </thead>
          <tbody>
            {allocations.length === 0 ? (
              <tr>
                <td className="px-3 py-8 text-center text-sm font-semibold text-slate-500" colSpan={5}>
                  No allocation records found.
                </td>
              </tr>
            ) : (
              allocations.map((allocation) => (
                <tr key={`${allocation.projectName}-${allocation.startDate}`} className="border-b border-slate-200">
                  <td className="px-3 py-4 text-sm font-semibold text-slate-950">{allocation.projectName}</td>
                  <td className="px-3 py-4 text-sm text-slate-700">{allocation.clientName}</td>
                  <td className="px-3 py-4 text-sm text-slate-700">{allocation.roleOnProject}</td>
                  <td className="px-3 py-4 text-sm text-slate-700">{formatNumber(allocation.allocationFTE)}</td>
                  <td className="px-3 py-4 text-sm text-slate-700">{allocation.allocationStatus || 'N/A'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function SideList({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="flex min-h-[210px] flex-col rounded-lg border border-slate-300 bg-white p-5">
      <h2 className="text-lg font-extrabold text-slate-950">{title}</h2>
      {items.length === 0 ? (
        <div className="mt-4 flex flex-1 items-center justify-center rounded-lg bg-slate-50 px-4 py-6 text-center text-sm font-semibold text-slate-500">
          No records found.
        </div>
      ) : (
        <ul className="mt-4 grid gap-3">
          {items.map((item) => (
            <li key={item} className="rounded-lg bg-slate-50 px-3 py-2 text-sm leading-6 text-slate-700">
              {item}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function AvailabilityPanel({
  items,
}: {
  items: PersonProfileResponse['availabilityForecast']
}) {
  return (
    <div className="rounded-lg border border-slate-300 bg-white p-5">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <h2 className="text-2xl font-extrabold text-slate-950">Availability Forecast</h2>
        <div className="text-sm font-semibold text-slate-500">
          Next {items.length} forecast records
        </div>
      </div>
      {items.length === 0 ? (
        <div className="mt-5 rounded-lg bg-slate-50 px-4 py-8 text-center text-sm font-semibold text-slate-500">
          No forecast records found.
        </div>
      ) : (
        <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {items.map((item) => (
            <div key={`${item.weekStartDate}-${item.availabilityType}`} className="rounded-lg bg-slate-50 p-4">
              <div className="text-sm font-extrabold text-slate-950">
                {formatDate(item.weekStartDate)}
              </div>
              <div className="mt-1 text-sm text-slate-600">
                {formatNumber(item.availableFTE)} FTE - {item.availabilityType || 'Availability'}
              </div>
              <div className="mt-2 text-xs font-semibold text-slate-500">
                {item.confidence || 'Confidence not set'} | {item.source || 'Source not set'}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default PersonProfilePage
