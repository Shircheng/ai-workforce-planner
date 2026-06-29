import {
  BriefcaseBusiness,
  CalendarDays,
  CheckCircle2,
  Clock3,
  Database,
  FileCheck2,
  Layers3,
  Loader2,
  MapPin,
  ShieldCheck,
  Sparkles,
  Users,
  X,
} from 'lucide-react'
import type { ReactNode, RefObject } from 'react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { recommendationApi } from '../api/recommendationApi'
import type { Opportunity } from '../types/Opportunity'
import type { OpportunityRole } from '../types/OpportunityRoles'
import type {
  MemberExplanation,
  NumericValue,
  OptionExplanation,
  RecommendationExplanationResponse,
  RecommendationOption,
  RecommendationRun,
  RecommendationRunMember,
} from '../types/Recommendation'

type SelectedMember = {
  option: RecommendationOption
  member: RecommendationRunMember
}

const optionOrder = [
  'BEST_SKILL_FIT',
  'FASTEST_AVAILABLE_TEAM',
  'BALANCED_LOW_RISK_TEAM',
]

const optionCopy: Record<
  string,
  { label: string; shortLabel: string; accent: string }
> = {
  BEST_SKILL_FIT: {
    label: 'Option A: Best Skill Fit',
    shortLabel: 'Option A',
    accent: 'green',
  },
  FASTEST_AVAILABLE_TEAM: {
    label: 'Option B: Fastest Available',
    shortLabel: 'Option B',
    accent: 'blue',
  },
  BALANCED_LOW_RISK_TEAM: {
    label: 'Option C: Balanced Low Risk',
    shortLabel: 'Option C',
    accent: 'orange',
  },
}

const notAvailable = 'Not available'
const defaultOpportunityId = 'OPP-001'

function RecommendationPage() {
  const { id: routeOpportunityId } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const opportunityId = normalizeOpportunityId(routeOpportunityId)
  const comparisonRef = useRef<HTMLDivElement | null>(null)
  const [opportunity, setOpportunity] = useState<Opportunity | null>(null)
  const [roles, setRoles] = useState<OpportunityRole[]>([])
  const [recommendationRun, setRecommendationRun] =
    useState<RecommendationRun | null>(null)
  const [explanation, setExplanation] =
    useState<RecommendationExplanationResponse | null>(null)
  const [selectedOptionType, setSelectedOptionType] = useState<string | null>(
    null,
  )
  const [selectedMember, setSelectedMember] = useState<SelectedMember | null>(
    null,
  )
  const [isLoading, setIsLoading] = useState(true)
  const [isGenerating, setIsGenerating] = useState(false)
  const [isExplaining, setIsExplaining] = useState(false)
  const [isPreparingEwa, setIsPreparingEwa] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [ewaMessage, setEwaMessage] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    async function loadPage() {
      if (routeOpportunityId !== opportunityId) {
        navigate(`/opportunities/${opportunityId}/recommendations`, {
          replace: true,
        })
      }

      setIsLoading(true)
      setError(null)
      setNotice(null)
      setExplanation(null)

      try {
        const latestRun =
          await recommendationApi.getLatestRecommendationRun(opportunityId)

        if (!active) {
          return
        }

        setRecommendationRun(latestRun)
        setSelectedOptionType(firstOptionType(latestRun))

        const [loadedOpportunity, loadedRoles] = await Promise.allSettled([
          recommendationApi.getOpportunity(opportunityId),
          recommendationApi.getOpportunityRoles(opportunityId),
        ])

        if (!active) {
          return
        }

        setOpportunity(
          loadedOpportunity.status === 'fulfilled'
            ? loadedOpportunity.value
            : null,
        )
        setRoles(
          loadedRoles.status === 'fulfilled' ? loadedRoles.value : [],
        )

        if (latestRun?.recommendationRunId) {
          const cachedExplanation =
            await recommendationApi.getRecommendationExplanation(
              latestRun.recommendationRunId,
            )
          if (active && cachedExplanation?.explanations) {
            setExplanation(cachedExplanation)
          }
        }
      } catch (loadError) {
        if (active) {
          setError(errorMessage(loadError))
        }
      } finally {
        if (active) {
          setIsLoading(false)
        }
      }
    }

    loadPage()

    return () => {
      active = false
    }
  }, [navigate, opportunityId, routeOpportunityId])

  const options = useMemo(
    () => sortOptions(recommendationRun?.options ?? []),
    [recommendationRun],
  )

  const selectedOption = useMemo(
    () =>
      options.find((option) => option.optionType === selectedOptionType) ??
      options[0] ??
      null,
    [options, selectedOptionType],
  )

  const roleCount = roles.length
  const totalFte = roles.reduce(
    (sum, role) => sum + (toNumber(role.fteRequired) ?? 0),
    0,
  )

  async function handleGenerateRecommendations() {
    if (!opportunityId) {
      return
    }

    setIsGenerating(true)
    setError(null)
    setNotice(null)
    setExplanation(null)
    setSelectedMember(null)

    try {
      const run = await recommendationApi.generateRecommendationRun(
        opportunityId,
      )
      setRecommendationRun(run)
      setSelectedOptionType(firstOptionType(run))
      setNotice('Recommendation run generated by the backend scoring engine.')
    } catch (generateError) {
      setError(errorMessage(generateError))
    } finally {
      setIsGenerating(false)
    }
  }

  async function handleGenerateExplanation() {
    if (!recommendationRun?.recommendationRunId) {
      return
    }

    setIsExplaining(true)
    setNotice(null)
    setError(null)

    try {
      const response = await recommendationApi.generateRecommendationExplanation(
        recommendationRun.recommendationRunId,
      )
      setExplanation(response)
      setRecommendationRun((current) =>
        current
          ? {
              ...current,
              explanationStatus:
                response.explanationStatus ?? 'AI_EXPLANATION_GENERATED',
            }
          : current,
      )
      setNotice(
        response.fallbackUsed
          ? 'AI was unavailable, so deterministic fallback explanations were saved.'
          : response.cached
            ? 'Cached AI explanation loaded from MongoDB.'
            : 'AI explanation generated and saved.',
      )
    } catch (explanationError) {
      setError(errorMessage(explanationError))
    } finally {
      setIsExplaining(false)
    }
  }

  async function handlePrepareEwaPack() {
    if (!recommendationRun?.recommendationRunId || !selectedOption?.optionType) {
      setEwaMessage('Select a recommendation option before preparing EWA.')
      return
    }

    setIsPreparingEwa(true)
    setEwaMessage(null)

    try {
      await recommendationApi.prepareEwaReviewPack(
        recommendationRun.recommendationRunId,
        selectedOption.optionType,
      )
      setEwaMessage('EWA review pack prepared for the selected option.')
    } catch {
      setEwaMessage(
        'EWA review pack is ready to prepare. EWA remains the final approval and booking process.',
      )
    } finally {
      setIsPreparingEwa(false)
    }
  }

  if (isLoading) {
    return (
      <section className="recommendation-page">
        <div className="recommendation-loading">
          <Loader2 className="spin" size={22} />
          <span>Loading recommendation workspace...</span>
        </div>
      </section>
    )
  }

  return (
    <section className="recommendation-page">
      <div className="recommendation-topbar">
        <div>
          <h1>Recommendations</h1>
          <p className="recommendation-subtitle">
            Evidence-backed staffing options generated from skill fit,
            availability, grade, location, domain, and project evidence.
          </p>
        </div>
        <div className="recommendation-actions">
          <button
            className="secondary-button"
            disabled={!recommendationRun}
            onClick={() =>
              comparisonRef.current?.scrollIntoView({ behavior: 'smooth' })
            }
            type="button"
          >
            <Layers3 size={16} />
            Compare Teams
          </button>
          <button
            className="secondary-button ai-button"
            disabled={!recommendationRun || isExplaining}
            onClick={handleGenerateExplanation}
            type="button"
          >
            {isExplaining ? (
              <Loader2 className="spin" size={16} />
            ) : (
              <Sparkles size={16} />
            )}
            Generate AI Explanation
          </button>
          <button
            className="primary-button"
            disabled={!selectedOption || isPreparingEwa}
            onClick={handlePrepareEwaPack}
            type="button"
          >
            {isPreparingEwa ? (
              <Loader2 className="spin" size={16} />
            ) : (
              <FileCheck2 size={16} />
            )}
            Prepare EWA Pack
          </button>
        </div>
      </div>

      {error ? <div className="message error-message">{error}</div> : null}
      {notice ? <div className="message success-message">{notice}</div> : null}
      {ewaMessage ? (
        <div className="message info-message">{ewaMessage}</div>
      ) : null}

      <OpportunitySummaryStrip
        explanationStatus={recommendationRun?.explanationStatus}
        opportunity={opportunity}
        recommendationRun={recommendationRun}
        roleCount={roleCount}
        totalFte={totalFte}
      />

      {!recommendationRun ? (
        <EmptyRecommendationState
          isGenerating={isGenerating}
          onGenerate={handleGenerateRecommendations}
          opportunityName={opportunity?.opportunityName}
        />
      ) : (
        <>
          <div className="run-toolbar">
            <div>
              <span className="eyebrow">Recommendation run</span>
              <strong>{recommendationRun.recommendationRunId}</strong>
              <span>{formatDateTime(recommendationRun.generatedAt)}</span>
            </div>
            <button
              className="secondary-button"
              disabled={isGenerating}
              onClick={handleGenerateRecommendations}
              type="button"
            >
              {isGenerating ? (
                <Loader2 className="spin" size={16} />
              ) : (
                <Database size={16} />
              )}
              Regenerate
            </button>
          </div>

          {explanation?.explanations?.runSummary ? (
            <div className="run-summary">
              <Sparkles size={18} />
              <div>
                <strong>AI run summary</strong>
                <p>{explanation.explanations.runSummary}</p>
              </div>
            </div>
          ) : null}

          <div className="option-grid">
            {options.map((option, index) => (
              <TeamOptionCard
                explanation={findOptionExplanation(explanation, option)}
                isSelected={selectedOption?.optionType === option.optionType}
                key={option.optionType ?? index}
                onSelect={() =>
                  setSelectedOptionType(option.optionType ?? null)
                }
                onSelectMember={(member) => setSelectedMember({ option, member })}
                option={option}
                optionIndex={index}
              />
            ))}
          </div>

          <TeamComparisonTable
            explanation={explanation}
            options={options}
            refNode={comparisonRef}
          />
        </>
      )}

      {selectedMember ? (
        <PersonDetailDrawer
          explanation={findMemberExplanation(
            explanation,
            selectedMember.option,
            selectedMember.member,
          )}
          member={selectedMember.member}
          onClose={() => setSelectedMember(null)}
          option={selectedMember.option}
        />
      ) : null}
    </section>
  )
}

type OpportunitySummaryStripProps = {
  opportunity: Opportunity | null
  recommendationRun: RecommendationRun | null
  explanationStatus?: string
  roleCount: number
  totalFte: number
}

function OpportunitySummaryStrip({
  opportunity,
  recommendationRun,
  explanationStatus,
  roleCount,
  totalFte,
}: OpportunitySummaryStripProps) {
  const location = [
    opportunity?.city,
    opportunity?.country,
    opportunity?.region,
  ]
    .filter(Boolean)
    .join(', ')

  return (
    <div className="opportunity-strip">
      <div className="opportunity-icon">
        <Database size={24} />
      </div>
      <div className="opportunity-main">
        <strong>
          {opportunity?.opportunityName ??
            recommendationRun?.opportunityName ??
            'Opportunity'}
        </strong>
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
      <div className="summary-status">
        <span>EWA / AI Status</span>
        <StatusBadge status={explanationStatus} />
      </div>
    </div>
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

function EmptyRecommendationState({
  isGenerating,
  onGenerate,
  opportunityName,
}: {
  isGenerating: boolean
  onGenerate: () => void
  opportunityName?: string
}) {
  return (
    <div className="empty-recommendations">
      <div className="empty-icon">
        <Users size={28} />
      </div>
      <h2>No recommendation run yet</h2>
      <p>
        Generate backend staffing options for{' '}
        <strong>{opportunityName ?? 'this opportunity'}</strong>. The frontend
        will display the stored run without recalculating scores.
      </p>
      <button
        className="primary-button"
        disabled={isGenerating}
        onClick={onGenerate}
        type="button"
      >
        {isGenerating ? <Loader2 className="spin" size={16} /> : <Users size={16} />}
        Generate Recommendations
      </button>
    </div>
  )
}

type TeamOptionCardProps = {
  option: RecommendationOption
  optionIndex: number
  explanation?: OptionExplanation
  isSelected: boolean
  onSelect: () => void
  onSelectMember: (member: RecommendationRunMember) => void
}

function TeamOptionCard({
  option,
  optionIndex,
  explanation,
  isSelected,
  onSelect,
  onSelectMember,
}: TeamOptionCardProps) {
  const config = optionConfig(option, optionIndex)
  const members = option.members ?? []
  const confidenceScore = toNumber(option.confidenceScore)

  return (
    <article
      className={`team-option-card accent-${config.accent} ${
        isSelected ? 'selected' : ''
      }`}
    >
      <button
        aria-label={`Select ${config.label}`}
        className="card-select-overlay"
        onClick={onSelect}
        type="button"
      />
      <div className="option-card-content">
        <div className="option-card-header">
          <div>
            <h2>{config.label}</h2>
            <span>{option.optionType ?? 'Backend option'}</span>
          </div>
          {isSelected ? (
            <span className="selected-pill">
              <CheckCircle2 size={13} />
              Selected
            </span>
          ) : null}
        </div>

        <div className="score-row">
          <div>
            <strong>{formatScore(confidenceScore)}</strong>
            <span>/100</span>
          </div>
          <RiskBadge riskLevel={option.riskLevel} />
        </div>

        <p className="option-summary">
          {explanation?.teamSummary ??
            firstText(option.risks) ??
            'Backend-generated recommendation option. Generate AI explanation for a concise team summary.'}
        </p>

        <div className="option-stats">
          <StatPill label="Readiness" value={readinessText(option)} />
          <StatPill
            label="Members"
            value={String(option.selectedMemberCount ?? members.length)}
          />
          <StatPill label="FTE gap" value={formatNumber(optionFteGap(option))} />
        </div>

        <div className="member-table">
          <div className="member-table-head">
            <span>Team Member</span>
            <span>Availability</span>
            <span>FTE</span>
            <span>Gap</span>
            <span>Score</span>
          </div>
          {members.map((member, index) => (
            <button
              className="member-row"
              key={`${member.employeeId ?? 'employee'}-${member.opportunityRoleId ?? index}`}
              onClick={() => onSelectMember(member)}
              type="button"
            >
              <span className="member-identity">
                <span className="avatar">{initials(member.employeeName)}</span>
                <span>
                  <strong>{member.employeeName ?? 'Unnamed employee'}</strong>
                  <small>{member.roleName ?? notAvailable}</small>
                  <em>
                    {member.fitStatus ?? 'Fit status unavailable'} · Cap{' '}
                    {formatScore(
                      toNumber(member.capabilityFitScore ?? member.matchScore),
                    )}{' '}
                    · Avail {formatScore(toNumber(member.availabilityFitScore))}
                  </em>
                </span>
              </span>
              <span>{availabilityText(member)}</span>
              <span>{formatNumber(toNumber(member.availableFteAtStart))}</span>
              <span>{formatNumber(toNumber(member.fteGap))}</span>
              <ScoreChip
                constrained={hasConstraint(member)}
                score={bestMemberScore(member)}
              />
            </button>
          ))}
        </div>

        <div className="risk-box">
          <ShieldCheck size={18} />
          <div>
            <strong>Risks / Gaps</strong>
            <p>{explanation?.riskSummary ?? risksText(option)}</p>
          </div>
        </div>
      </div>
    </article>
  )
}

function StatPill({ label, value }: { label: string; value: string }) {
  return (
    <span className="stat-pill">
      <small>{label}</small>
      <strong>{value}</strong>
    </span>
  )
}

function ScoreChip({
  constrained,
  score,
}: {
  constrained: boolean
  score: number | null
}) {
  const tone = scoreTone(score, constrained)
  return <span className={`score-chip ${tone}`}>{formatScore(score)}</span>
}

function TeamComparisonTable({
  explanation,
  options,
  refNode,
}: {
  explanation: RecommendationExplanationResponse | null
  options: RecommendationOption[]
  refNode: RefObject<HTMLDivElement | null>
}) {
  const rows = [
    {
      label: 'Overall score / confidence',
      value: (option: RecommendationOption) =>
        `${formatScore(toNumber(option.confidenceScore))} /100`,
    },
    {
      label: 'Risk level',
      value: (option: RecommendationOption) => option.riskLevel ?? notAvailable,
    },
    {
      label: 'Readiness days',
      value: (option: RecommendationOption) => readinessText(option),
    },
    {
      label: 'Skill coverage',
      value: (option: RecommendationOption) => memberRationaleText(option),
    },
    {
      label: 'Availability readiness',
      value: (option: RecommendationOption) => optionAvailabilityText(option),
    },
    {
      label: 'FTE gap',
      value: (option: RecommendationOption) => formatNumber(optionFteGap(option)),
    },
    {
      label: 'Key risks',
      value: (option: RecommendationOption) => risksText(option),
    },
    {
      label: 'AI next actions',
      value: (option: RecommendationOption) =>
        nextActionsText(findOptionExplanation(explanation, option)),
    },
    {
      label: 'EWA readiness',
      value: (option: RecommendationOption) =>
        findOptionExplanation(explanation, option)?.ewaSummary ??
        'EWA remains the final approval and booking process.',
    },
  ]

  return (
    <div className="comparison-section" ref={refNode}>
      <div className="section-heading">
        <h2>Team Comparison</h2>
        <p>
          Side-by-side comparison of backend-generated staffing options across
          key workforce planning criteria.
        </p>
      </div>
      <div className="comparison-table-wrap">
        <table className="comparison-table">
          <thead>
            <tr>
              <th>Criteria</th>
              {options.map((option, index) => (
                <th key={option.optionType ?? index}>
                  {optionConfig(option, index).label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.label}>
                <td>{row.label}</td>
                {options.map((option, index) => (
                  <td key={`${row.label}-${option.optionType ?? index}`}>
                    {row.value(option)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function PersonDetailDrawer({
  explanation,
  member,
  onClose,
  option,
}: {
  explanation?: MemberExplanation
  member: RecommendationRunMember
  onClose: () => void
  option: RecommendationOption
}) {
  return (
    <aside className="person-drawer" aria-label="Person recommendation details">
      <button
        aria-label="Close person details"
        className="drawer-close"
        onClick={onClose}
        type="button"
      >
        <X size={18} />
      </button>

      <div className="drawer-profile">
        <div className="drawer-avatar">{initials(member.employeeName)}</div>
        <div>
          <h2>{member.employeeName ?? 'Unnamed employee'}</h2>
          <p>{member.roleName ?? notAvailable}</p>
          <span>
            {member.fitStatus ?? 'Fit status unavailable'} ·{' '}
            {optionConfig(option, 0).label}
          </span>
        </div>
      </div>

      <div className="drawer-section">
        <h3>Scores</h3>
        <div className="drawer-score-grid">
          <DrawerScore
            label="Skill Fit"
            value={toNumber(member.capabilityFitScore ?? member.matchScore)}
          />
          <DrawerScore
            label="Availability"
            value={toNumber(member.availabilityFitScore)}
          />
          <DrawerScore
            label="Overall"
            value={toNumber(member.overallStaffingScore)}
          />
        </div>
      </div>

      <div className="drawer-section">
        <h3>Role & Availability</h3>
        <dl className="drawer-details">
          <div>
            <dt>Opportunity Role</dt>
            <dd>{member.opportunityRoleId ?? notAvailable}</dd>
          </div>
          <div>
            <dt>Available FTE at Start</dt>
            <dd>{formatNumber(toNumber(member.availableFteAtStart))}</dd>
          </div>
          <div>
            <dt>FTE Gap</dt>
            <dd>{formatNumber(toNumber(member.fteGap))}</dd>
          </div>
          <div>
            <dt>Earliest Full Availability</dt>
            <dd>{formatDate(member.earliestFullAvailabilityDate)}</dd>
          </div>
        </dl>
      </div>

      <div className="drawer-section">
        <h3>Backend Evidence</h3>
        <div className="evidence-box">
          <strong>Rationale</strong>
          <p>{member.rationale ?? notAvailable}</p>
        </div>
        <div className={`evidence-box ${hasConstraint(member) ? 'warn' : ''}`}>
          <strong>Risk / Constraint</strong>
          <p>{member.constraint ?? 'None'}</p>
        </div>
      </div>

      <div className="drawer-section">
        <h3>AI Recommendation</h3>
        {explanation ? (
          <div className="ai-note">
            <Sparkles size={17} />
            <div>
              <p>{explanation.recommendationNote ?? notAvailable}</p>
              {explanation.reasoningBullets?.length ? (
                <ul>
                  {explanation.reasoningBullets.map((bullet) => (
                    <li key={bullet}>{bullet}</li>
                  ))}
                </ul>
              ) : null}
            </div>
          </div>
        ) : (
          <p className="muted-copy">
            Generate AI explanation to show recommendation note, reasoning,
            risks, next actions, and EWA summary for this member.
          </p>
        )}
      </div>

      <div className="drawer-section">
        <h3>EWA Summary</h3>
        <div className="ewa-box">
          <FileCheck2 size={17} />
          <span>
            {explanation?.ewaSummary ??
              'EWA remains the final approval and booking process.'}
          </span>
        </div>
      </div>
    </aside>
  )
}

function DrawerScore({
  label,
  value,
}: {
  label: string
  value: number | null
}) {
  return (
    <div className={`drawer-score ${scoreTone(value, false)}`}>
      <strong>{formatScore(value)}</strong>
      <span>{label}</span>
    </div>
  )
}

function StatusBadge({ status }: { status?: string }) {
  const normalized = status ?? 'AI_EXPLANATION_NOT_GENERATED'
  const generated = normalized === 'AI_EXPLANATION_GENERATED'

  return (
    <span className={`status-badge ${generated ? 'generated' : 'pending'}`}>
      {generated ? 'AI Generated' : readableStatus(normalized)}
    </span>
  )
}

function RiskBadge({ riskLevel }: { riskLevel?: string }) {
  const normalized = (riskLevel ?? '').toLowerCase()
  const tone = normalized.includes('low')
    ? 'low'
    : normalized.includes('high')
      ? 'high'
      : 'medium'

  return <span className={`risk-badge ${tone}`}>{riskLevel ?? 'Risk n/a'}</span>
}

function firstOptionType(run: RecommendationRun | null) {
  return sortOptions(run?.options ?? [])[0]?.optionType ?? null
}

function normalizeOpportunityId(value?: string) {
  if (!value) {
    return defaultOpportunityId
  }

  const decoded = safeDecode(value).trim()
  if (
    !decoded ||
    decoded === 'sample' ||
    decoded === ':opportunityId' ||
    decoded.includes('{') ||
    decoded.includes('}')
  ) {
    return defaultOpportunityId
  }

  return decoded
}

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function sortOptions(options: RecommendationOption[]) {
  return [...options].sort((left, right) => {
    const leftIndex = optionOrder.indexOf(left.optionType ?? '')
    const rightIndex = optionOrder.indexOf(right.optionType ?? '')
    return normalizeOrder(leftIndex) - normalizeOrder(rightIndex)
  })
}

function normalizeOrder(index: number) {
  return index === -1 ? 99 : index
}

function optionConfig(option: RecommendationOption, index: number) {
  if (option.optionType && optionCopy[option.optionType]) {
    return optionCopy[option.optionType]
  }

  const labels = ['Option A', 'Option B', 'Option C']
  const accents = ['green', 'blue', 'orange']
  const label = `${labels[index] ?? `Option ${index + 1}`}: ${titleCase(
    option.optionType ?? 'Recommendation',
  )}`

  return {
    label,
    shortLabel: labels[index] ?? `Option ${index + 1}`,
    accent: accents[index] ?? 'green',
  }
}

function findOptionExplanation(
  response: RecommendationExplanationResponse | null,
  option: RecommendationOption,
) {
  return response?.explanations?.options?.find(
    (item) => item.optionType === option.optionType,
  )
}

function findMemberExplanation(
  response: RecommendationExplanationResponse | null,
  option: RecommendationOption,
  member: RecommendationRunMember,
) {
  const optionExplanation = findOptionExplanation(response, option)
  return optionExplanation?.members?.find(
    (item) =>
      item.employeeId === member.employeeId &&
      item.opportunityRoleId === member.opportunityRoleId,
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

function formatScore(value: NumericValue) {
  const score = toNumber(value)
  return score === null ? '--' : Math.round(score).toString()
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

function formatDateTime(value?: string) {
  if (!value) {
    return 'Generated date unavailable'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
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

function readinessText(option: RecommendationOption) {
  if (option.readinessDays === undefined || option.readinessDays === null) {
    return notAvailable
  }
  return option.readinessDays === 0
    ? 'Ready now'
    : `${option.readinessDays} day${option.readinessDays === 1 ? '' : 's'}`
}

function availabilityText(member: RecommendationRunMember) {
  return member.earliestFullAvailabilityDate
    ? formatDate(member.earliestFullAvailabilityDate)
    : formatNumber(toNumber(member.availableFteAtStart))
}

function optionAvailabilityText(option: RecommendationOption) {
  const members = option.members ?? []
  if (!members.length) {
    return notAvailable
  }

  return members
    .map(
      (member) =>
        `${member.employeeName ?? 'Member'}: ${availabilityText(member)}`,
    )
    .slice(0, 3)
    .join('; ')
}

function memberRationaleText(option: RecommendationOption) {
  const rationales = (option.members ?? [])
    .map((member) => member.rationale)
    .filter(Boolean)

  return rationales.length ? rationales.slice(0, 2).join(' | ') : notAvailable
}

function risksText(option: RecommendationOption) {
  return option.risks?.length ? option.risks.join('; ') : 'No risks supplied.'
}

function nextActionsText(explanation?: OptionExplanation) {
  return explanation?.nextActions?.length
    ? explanation.nextActions.join('; ')
    : 'Generate AI explanation for next actions.'
}

function optionFteGap(option: RecommendationOption) {
  return (option.members ?? []).reduce(
    (sum, member) => sum + (toNumber(member.fteGap) ?? 0),
    0,
  )
}

function bestMemberScore(member: RecommendationRunMember) {
  return toNumber(member.overallStaffingScore ?? member.matchScore)
}

function hasConstraint(member: RecommendationRunMember) {
  const constraint = member.constraint?.trim().toLowerCase()
  const fteGap = toNumber(member.fteGap)
  return Boolean(
    (constraint && constraint !== 'none') || (fteGap !== null && fteGap > 0),
  )
}

function scoreTone(score: number | null, constrained: boolean) {
  if (constrained || score === null || score < 75) {
    return 'warn'
  }
  if (score >= 85) {
    return 'strong'
  }
  return 'medium'
}

function initials(name?: string) {
  if (!name) {
    return 'NA'
  }
  const parts = name.trim().split(/\s+/).slice(0, 2)
  return parts.map((part) => part[0]?.toUpperCase()).join('') || 'NA'
}

function firstText(values?: string[]) {
  return values?.find((value) => Boolean(value?.trim()))
}

function readableStatus(status: string) {
  return titleCase(status.replace(/^AI_EXPLANATION_/, 'AI '))
}

function titleCase(value: string) {
  return value
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase())
}

function errorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }
  return 'Unexpected request failure.'
}

export default RecommendationPage
