import {
  BriefcaseBusiness,
  CalendarDays,
  ChevronDown,
  Clock3,
  Database,
  MapPin,
  Users,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import type { Opportunity } from '../../types/Opportunity'
import type { OpportunityRole } from '../../types/OpportunityRoles'
import type { NumericValue, RecommendationRun } from '../../types/Recommendation'

const notAvailable = 'Not available'

type OpportunityDetailsCardProps = {
  opportunity: Opportunity | null
  recommendationRun: RecommendationRun | null
  roleCount: number
  roles: OpportunityRole[]
  totalFte: number
}

function OpportunityDetailsCard({
  opportunity,
  recommendationRun,
  roleCount,
  roles,
  totalFte,
}: OpportunityDetailsCardProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const location = [
    opportunity?.city,
    opportunity?.country,
    opportunity?.region,
  ]
    .filter(Boolean)
    .join(', ')
  const opportunityName =
    opportunity?.opportunityName ??
    recommendationRun?.opportunityName ??
    'Opportunity'

  return (
    <section className={`opportunity-card ${isExpanded ? 'expanded' : ''}`}>
      <button
        aria-expanded={isExpanded}
        className="opportunity-strip"
        onClick={() => setIsExpanded((current) => !current)}
        type="button"
      >
        <div className="opportunity-icon">
          <Database size={24} />
        </div>
        <div className="opportunity-main">
          <strong>{opportunityName}</strong>
          <span>
            {[opportunity?.domain, opportunity?.clientName]
              .filter(Boolean)
              .join(' / ') || notAvailable}
          </span>
        </div>
        <SummaryMetric
          icon={<CalendarDays size={15} />}
          label="Start Date"
          value={formatDate(opportunity?.expectedStartDate)}
        />
        <SummaryMetric
          icon={<Clock3 size={15} />}
          label="Duration"
          value={formatDuration(opportunity?.durationWeeks)}
        />
        <SummaryMetric
          icon={<MapPin size={15} />}
          label="Location"
          value={location || notAvailable}
        />
        <SummaryMetric
          icon={<Users size={15} />}
          label="Key Roles"
          value={roleCount ? String(roleCount) : notAvailable}
        />
        <SummaryMetric
          icon={<BriefcaseBusiness size={15} />}
          label="Total FTE"
          value={totalFte ? formatNumber(totalFte) : notAvailable}
        />
        <span className="opportunity-toggle">
          <ChevronDown size={18} />
        </span>
      </button>

      {isExpanded ? (
        <div className="opportunity-detail">
          <div className="opportunity-detail-section">
            <div className="opportunity-detail-heading">
              <strong>Opportunity Info</strong>
            </div>
            <div className="opportunity-info-grid">
              <DetailItem label="Opportunity name" value={opportunityName} />
              <DetailItem
                label="Client name"
                value={opportunity?.clientName ?? notAvailable}
              />
              <DetailItem
                label="Domain"
                value={opportunity?.domain ?? notAvailable}
              />
              <DetailItem
                label="Start date"
                value={formatDate(opportunity?.expectedStartDate)}
              />
              <DetailItem
                label="Duration"
                value={formatDuration(opportunity?.durationWeeks)}
              />
              <DetailItem label="Location" value={location || notAvailable} />
              <DetailItem
                label="Total FTE"
                value={totalFte ? formatNumber(totalFte) : notAvailable}
              />
              <DetailItem
                label="Number of roles"
                value={roleCount ? String(roleCount) : notAvailable}
              />
            </div>
          </div>

          <div className="opportunity-detail-section">
            <div className="opportunity-detail-heading">
              <strong>Role Info</strong>
            </div>
            {roles.length ? (
              <div className="role-detail-list">
                {roles.map((role) => (
                  <RoleDetailRow key={role.opportunityRoleId} role={role} />
                ))}
              </div>
            ) : (
              <span className="comparison-muted">No role details loaded.</span>
            )}
          </div>
        </div>
      ) : null}
    </section>
  )
}

function SummaryMetric({
  icon,
  label,
  value,
}: {
  icon: ReactNode
  label: string
  value: string
}) {
  return (
    <div className="summary-metric">
      <span>
        {icon}
        {label}
      </span>
      <strong>{value}</strong>
    </div>
  )
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail-item">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function RoleDetailRow({ role }: { role: OpportunityRole }) {
  return (
    <div className="role-detail-row">
      <div className="role-detail-main">
        <strong>{role.roleName || notAvailable}</strong>
        <span>{role.disciplineOrDepartment || notAvailable}</span>
      </div>
      <div className="role-detail-meta">
        <DetailItem
          label="Grade preference"
          value={role.gradePreference || notAvailable}
        />
        <DetailItem
          label="FTE required"
          value={formatNumber(toNumber(role.fteRequired))}
        />
        <DetailItem label="Priority" value={role.priority || notAvailable} />
        <DetailItem
          label="Location preference"
          value={role.locationPreference || notAvailable}
        />
      </div>
      <div className="role-skill-grid">
        <RoleSkillGroup
          emptyText="No required skills"
          label="Required skills"
          skills={role.requiredSkills}
          tone="required"
        />
        <RoleSkillGroup
          emptyText="No desired skills"
          label="Desired skills"
          skills={role.desiredSkills}
          tone="desired"
        />
      </div>
    </div>
  )
}

function RoleSkillGroup({
  emptyText,
  label,
  skills,
  tone,
}: {
  emptyText: string
  label: string
  skills?: string[]
  tone: 'required' | 'desired'
}) {
  const visibleSkills = normalizedList(skills)

  return (
    <div className={`role-skill-group ${tone}`}>
      <span>{label}</span>
      {visibleSkills.length ? (
        <div>
          {visibleSkills.map((skill) => (
            <small key={skill}>{skill}</small>
          ))}
        </div>
      ) : (
        <em>{emptyText}</em>
      )}
    </div>
  )
}

function toNumber(value: NumericValue) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function formatNumber(value: NumericValue) {
  const number = toNumber(value)
  if (number === null) {
    return notAvailable
  }
  return number.toLocaleString(undefined, {
    maximumFractionDigits: 1,
    minimumFractionDigits: Number.isInteger(number) ? 0 : 1,
  })
}

function formatDate(value?: string) {
  if (!value) {
    return notAvailable
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date)
}

function formatDuration(durationWeeks?: number) {
  if (!durationWeeks) {
    return notAvailable
  }
  if (durationWeeks >= 4) {
    const months = durationWeeks / 4
    return `${Number.isInteger(months) ? months : months.toFixed(1)} months`
  }
  return `${durationWeeks} weeks`
}

function normalizedList(items?: string[] | null) {
  const values = items
    ?.map((item) => item?.trim())
    .filter((item): item is string => Boolean(item)) ?? []

  return Array.from(new Set(values))
}

export default OpportunityDetailsCard
