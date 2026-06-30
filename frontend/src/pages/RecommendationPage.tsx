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
import type { EwaRecommendationSelectionPayload } from '../api/ewaApi'
import { recommendationApi } from '../api/recommendationApi'
import './RecommandationPage.css'
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

type MemberWithOverlayReference = RecommendationRunMember & {
  opportunityOverlayId?: string
  overlayId?: string
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

  const hasExplanation = Boolean(explanation?.explanations)
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

  function handlePrepareEwaPack() {
    if (!recommendationRun?.recommendationRunId || !selectedOption) {
      setEwaMessage('Select a recommendation option before preparing EWA.')
      return
    }

    const selectedOptionIndex = Math.max(
      options.findIndex(
        (option) => option.optionType === selectedOption.optionType,
      ),
      0,
    )
    const payload = buildEwaSelectionPayload(
      recommendationRun.opportunityId ?? opportunityId,
      selectedOption,
      selectedOptionIndex,
    )

    if (!payload.selectedCandidates.length) {
      setEwaMessage('Selected option has no candidate data for EWA.')
      return
    }

    setEwaMessage(null)
    navigate('/ewa', { state: payload })
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
            className="primary-button"
            disabled={!selectedOption}
            onClick={handlePrepareEwaPack}
            type="button"
          >
            <FileCheck2 size={16} />
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
        opportunity={opportunity}
        recommendationRun={recommendationRun}
        roleCount={roleCount}
        totalFte={totalFte}
      />

      {recommendationRun && !hasExplanation ? (
        <div className="ai-explanation-cta">
          <div className="ai-explanation-cta-icon">
            <Sparkles size={18} />
          </div>
          <div>
            <strong>AI explanation not generated yet</strong>
            <p>
              Generate team and member explanations from this stored
              recommendation run. Backend scores and ranking will not change.
            </p>
          </div>
          <button
            className="secondary-button ai-explanation-button"
            disabled={isExplaining}
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
        </div>
      ) : null}

      {!recommendationRun ? (
        <EmptyRecommendationState
          isGenerating={isGenerating}
          onGenerate={handleGenerateRecommendations}
          opportunityName={opportunity?.opportunityName}
        />
      ) : (
        <>
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
            isExplaining={isExplaining}
            onGenerateExplanation={handleGenerateExplanation}
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
          isExplaining={isExplaining}
          member={selectedMember.member}
          onClose={() => setSelectedMember(null)}
          onGenerateExplanation={handleGenerateExplanation}
          option={selectedMember.option}
        />
      ) : null}
    </section>
  )
}

type OpportunitySummaryStripProps = {
  opportunity: Opportunity | null
  recommendationRun: RecommendationRun | null
  roleCount: number
  totalFte: number
}

function OpportunitySummaryStrip({
  opportunity,
  recommendationRun,
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
          </div>
          {isSelected ? (
            <span className="selected-pill">
              <CheckCircle2 size={16} />
              Selected Option
            </span>
          ) : null}
        </div>

        <div className="score-row">
          <div className="score-main">
            <strong>{formatScore(confidenceScore)}</strong>
            <span>/100</span>
            <small>Confidence</small>
          </div>
          <div className="risk-summary">
            <small>Risk</small>
            <RiskBadge riskLevel={option.riskLevel} />
          </div>
        </div>

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
                  <small className="member-role-pill">
                    {member.roleName ?? notAvailable}
                  </small>
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

        <TeamRiskSummary explanation={explanation} option={option} />
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

function TeamRiskSummary({
  explanation,
  option,
}: {
  explanation?: OptionExplanation
  option: RecommendationOption
}) {
  const items = teamRiskItems(option, explanation)
  const hasRisks = items.length > 0

  return (
    <div className={`risk-box ${hasRisks ? 'warn' : 'clear'}`}>
      <ShieldCheck size={18} />
      <div className="risk-box-content">
        <div className="risk-box-heading">
          <strong>Risks / Gaps</strong>
        </div>
        {hasRisks ? (
          <ul className="risk-box-list">
            {items.map((item, index) => (
              <li key={`${item}-${index}`}>{item}</li>
            ))}
          </ul>
        ) : (
          <p className="risk-box-empty">No risks supplied by the backend run.</p>
        )}
      </div>
    </div>
  )
}

function TeamComparisonTable({
  explanation,
  isExplaining,
  onGenerateExplanation,
  options,
  refNode,
}: {
  explanation: RecommendationExplanationResponse | null
  isExplaining: boolean
  onGenerateExplanation: () => void
  options: RecommendationOption[]
  refNode: RefObject<HTMLDivElement | null>
}) {
  const hasExplanation = Boolean(explanation?.explanations)
  const rows = [
    {
      description: 'Backend confidence for this option.',
      icon: <Database size={15} />,
      label: 'Overall score / confidence',
      render: (option: RecommendationOption) => (
        <ComparisonMetric
          label="Confidence"
          tone={scoreTone(toNumber(option.confidenceScore), false)}
          value={`${formatScore(toNumber(option.confidenceScore))}/100`}
        />
      ),
    },
    {
      description: 'Risk signal returned by the recommendation run.',
      icon: <ShieldCheck size={15} />,
      label: 'Risk level',
      render: (option: RecommendationOption) => (
        <div className="comparison-stack">
          <RiskBadge riskLevel={option.riskLevel} />
          <span className="comparison-muted">
            Risk score {formatScore(toNumber(option.riskScore))}
          </span>
        </div>
      ),
    },
    {
      description: 'How soon the team can be ready.',
      icon: <Clock3 size={15} />,
      label: 'Readiness days',
      render: (option: RecommendationOption) => (
        <ComparisonReadinessPill option={option} />
      ),
    },
    {
      description: 'Evidence from member rationale fields.',
      icon: <Layers3 size={15} />,
      label: 'Skill coverage',
      render: (option: RecommendationOption) => <SkillCoverageList option={option} />,
    },
    {
      description: 'Member availability and start readiness.',
      icon: <CalendarDays size={15} />,
      label: 'Availability readiness',
      render: (option: RecommendationOption) => (
        <ComparisonBulletList
          emptyText={notAvailable}
          items={optionAvailabilityItems(option)}
        />
      ),
    },
    {
      description: 'Summed FTE gap across selected members.',
      icon: <Users size={15} />,
      label: 'FTE gap',
      render: (option: RecommendationOption) => {
        const gap = optionFteGap(option)
        return (
          <ComparisonMetric
            label={gap > 0 ? 'Gap to resolve' : 'No gap'}
            tone={gap > 0 ? 'warn' : 'strong'}
            value={formatNumber(gap)}
          />
        )
      },
    },
    {
      description: 'Known gaps and constraints from backend output.',
      icon: <ShieldCheck size={15} />,
      label: 'Key risks',
      render: (option: RecommendationOption) => (
        <ComparisonBulletList
          emptyText="No risks supplied."
          items={riskItems(option)}
          tone="risk"
        />
      ),
    },
    {
      description: 'Generated only by the AI explanation endpoint.',
      icon: <Sparkles size={15} />,
      isAiNextActions: true,
      label: 'AI next actions',
      render: (option: RecommendationOption) => (
        <ComparisonBulletList
          emptyText="Generate AI explanation for next actions."
          items={nextActionItems(findOptionExplanation(explanation, option))}
          tone="action"
        />
      ),
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
                  <ComparisonOptionHeader option={option} optionIndex={index} />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const mergeAiActionColumns = row.isAiNextActions && !hasExplanation

              return (
                <tr key={row.label}>
                  <td>
                    <div className="comparison-criteria">
                      <span className="comparison-criteria-icon">{row.icon}</span>
                      <span>
                        <strong>{row.label}</strong>
                        <small>{row.description}</small>
                      </span>
                    </div>
                  </td>
                  {mergeAiActionColumns ? (
                    <td colSpan={Math.max(options.length, 1)}>
                      <GenerateAiExplanationPanel
                        compact
                        disabled={isExplaining}
                        onGenerate={onGenerateExplanation}
                      />
                    </td>
                  ) : (
                    options.map((option, index) => (
                      <td key={`${row.label}-${option.optionType ?? index}`}>
                        {row.render(option)}
                      </td>
                    ))
                  )}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function GenerateAiExplanationPanel({
  compact = false,
  disabled,
  onGenerate,
}: {
  compact?: boolean
  disabled: boolean
  onGenerate: () => void
}) {
  return (
    <div className={`generate-ai-panel ${compact ? 'compact' : ''}`}>
      <div className="generate-ai-icon">
        <Sparkles size={compact ? 16 : 18} />
      </div>
      <div className="generate-ai-copy">
        <strong>AI explanation not generated yet</strong>
        <p>
          Generate AI explanations from the stored recommendation run. Backend
          scores and ranking will not change.
        </p>
      </div>
      <button
        className="secondary-button ai-explanation-button"
        disabled={disabled}
        onClick={onGenerate}
        type="button"
      >
        {disabled ? <Loader2 className="spin" size={16} /> : <Sparkles size={16} />}
        Generate AI Explanation
      </button>
    </div>
  )
}

function ComparisonOptionHeader({
  option,
  optionIndex,
}: {
  option: RecommendationOption
  optionIndex: number
}) {
  const config = optionConfig(option, optionIndex)
  const members = option.selectedMemberCount ?? option.members?.length ?? 0

  return (
    <div className={`comparison-option-head accent-${config.accent}`}>
      <span>{config.shortLabel}</span>
      <strong>{config.label.replace(`${config.shortLabel}: `, '')}</strong>
      <div className="comparison-head-meta">
        <span>{members} members</span>
        <span>{readinessText(option)}</span>
      </div>
    </div>
  )
}

function ComparisonMetric({
  label,
  tone,
  value,
}: {
  label: string
  tone: string
  value: string
}) {
  return (
    <div className={`comparison-metric ${tone}`}>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  )
}

function ComparisonReadinessPill({ option }: { option: RecommendationOption }) {
  const readiness = readinessPill(option)

  return (
    <span className={`readiness-pill ${readiness.tone}`}>
      {readiness.label}
    </span>
  )
}

function SkillCoverageList({ option }: { option: RecommendationOption }) {
  const members = (option.members ?? []).slice(0, 4)

  if (!members.length) {
    return <span className="comparison-muted">No skill rationale supplied.</span>
  }

  return (
    <div className="comparison-skill-list">
      {members.map((member, index) => {
        const scorePills = memberSkillScorePills(member)

        return (
          <div
            className="comparison-skill-card"
            key={`${member.employeeId ?? 'member'}-${member.opportunityRoleId ?? index}`}
          >
            <strong>
              {member.employeeName ?? 'Member'} - {member.roleName ?? 'Role'}
            </strong>
            <div className="comparison-skill-lines">
              {memberSkillEvidenceLines(member).map((line, lineIndex) => (
                <span key={`${line}-${lineIndex}`}>{line}</span>
              ))}
            </div>
            {scorePills.length ? (
              <div className="comparison-skill-scores">
                {scorePills.map((score) => (
                  <span key={score.label}>
                    {score.label} {score.value}
                  </span>
                ))}
              </div>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}

function ComparisonBulletList({
  emptyText,
  items,
  tone = 'default',
}: {
  emptyText: string
  items: string[]
  tone?: 'default' | 'risk' | 'action'
}) {
  const visibleItems = items.filter(Boolean).slice(0, 4)

  if (!visibleItems.length) {
    return <span className="comparison-muted">{emptyText}</span>
  }

  return (
    <ul className={`comparison-detail-list ${tone}`}>
      {visibleItems.map((item, index) => (
        <li key={`${item}-${index}`}>{item}</li>
      ))}
    </ul>
  )
}

function PersonDetailDrawer({
  explanation,
  isExplaining,
  member,
  onClose,
  onGenerateExplanation,
  option,
}: {
  explanation?: MemberExplanation
  isExplaining: boolean
  member: RecommendationRunMember
  onClose: () => void
  onGenerateExplanation: () => void
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
            {member.fitStatus ?? 'Fit status unavailable'} -{' '}
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
        <h3>Evidence</h3>
        <MemberRationaleSummary member={member} />
        <div className={`evidence-box ${hasConstraint(member) ? 'warn' : ''}`}>
          <div className="evidence-heading">
            <strong>Risk / Constraint</strong>
            <span>{hasConstraint(member) ? 'Needs review' : 'Clear'}</span>
          </div>
          <p>{member.constraint ?? 'No constraint recorded.'}</p>
        </div>
      </div>

      <div className="drawer-section">
        <h3>AI Summary</h3>
        {explanation ? (
          <div className="ai-summary-stack">
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
            {explanation.nextActions?.length ? (
              <div className="ai-next-actions">
                <strong>Next Actions</strong>
                <ul>
                  {explanation.nextActions.map((action) => (
                    <li key={action}>{action}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        ) : (
          <GenerateAiExplanationPanel
            compact
            disabled={isExplaining}
            onGenerate={onGenerateExplanation}
          />
        )}
      </div>
    </aside>
  )
}

function MemberRationaleSummary({ member }: { member: RecommendationRunMember }) {
  const rationaleLines = memberSkillEvidenceLines(member)

  return (
    <div className="evidence-box rationale-box">
      <div className="evidence-heading">
        <strong>Rationale</strong>
        <span>{member.fitStatus ?? 'Backend evidence'}</span>
      </div>
      {rationaleLines.length ? (
        <ul className="evidence-list">
          {rationaleLines.map((line, index) => (
            <li key={`${line}-${index}`}>{line}</li>
          ))}
        </ul>
      ) : (
        <p>{member.rationale ?? notAvailable}</p>
      )}
    </div>
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

function readinessPill(option: RecommendationOption) {
  const days = option.readinessDays
  if (days === undefined || days === null) {
    return { label: 'Readiness unavailable', tone: 'neutral' }
  }

  if (days === 0) {
    return { label: 'Ready now', tone: 'strong' }
  }

  const hasOpenGap = optionFteGap(option) > 0
  const hasOpenConstraint = (option.members ?? []).some(hasConstraint)
  const mostlyReady = hasOpenGap || hasOpenConstraint
  const tone = days <= 30 && !mostlyReady ? 'strong' : 'warn'
  const prefix = mostlyReady ? 'Mostly ready' : 'Ready'

  return {
    label: `${prefix} in ${days} day${days === 1 ? '' : 's'}`,
    tone,
  }
}

function availabilityText(member: RecommendationRunMember) {
  return member.earliestFullAvailabilityDate
    ? formatDate(member.earliestFullAvailabilityDate)
    : formatNumber(toNumber(member.availableFteAtStart))
}

function optionAvailabilityItems(option: RecommendationOption) {
  const members = option.members ?? []
  if (!members.length) {
    return []
  }

  return members
    .map(
      (member) =>
        `${member.employeeName ?? 'Member'}: ${availabilityText(member)} availability, ${formatNumber(
          toNumber(member.availableFteAtStart),
        )} FTE at start`,
    )
    .slice(0, 4)
}

function memberSkillEvidenceLines(member: RecommendationRunMember) {
  const rationaleLines = splitEvidenceLines(member.rationale)
  if (rationaleLines.length) {
    return rationaleLines
  }

  const fallbackLines = [
    member.fitStatus ? `Fit status: ${member.fitStatus}` : null,
    `${formatNumber(toNumber(member.availableFteAtStart))} FTE available at start`,
    `FTE gap: ${formatNumber(toNumber(member.fteGap))}`,
  ]

  return fallbackLines.filter((line): line is string => Boolean(line))
}

function splitEvidenceLines(value?: string) {
  if (!value?.trim()) {
    return []
  }

  return value
    .split(';')
    .map((line) => line.replace(/\s+/g, ' ').trim())
    .filter((line) => Boolean(line) && !isScoreOnlyEvidenceLine(line))
}

function isScoreOnlyEvidenceLine(value: string) {
  return /^(capability|availability|overall)(?:\s+(?:score|fit))?\s*[:=-]?\s*\d+(?:\.\d+)?\.?$/i.test(
    value,
  )
}

function memberSkillScorePills(member: RecommendationRunMember) {
  return [
    {
      label: 'Capability',
      value: toNumber(member.capabilityFitScore ?? member.matchScore),
    },
    { label: 'Availability', value: toNumber(member.availabilityFitScore) },
    { label: 'Overall', value: toNumber(member.overallStaffingScore) },
  ]
    .filter((score) => score.value !== null)
    .map((score) => ({
      label: score.label,
      value: formatScore(score.value),
    }))
}

function teamRiskItems(
  option: RecommendationOption,
  explanation?: OptionExplanation,
) {
  const aiRiskItems = splitTextItems(explanation?.riskSummary)
  if (aiRiskItems.length) {
    return aiRiskItems.slice(0, 4)
  }

  return riskItems(option)
}

function splitTextItems(value?: string) {
  if (!value?.trim()) {
    return []
  }

  return value
    .split(/\n|;/)
    .map((item) => item.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
}

function buildEwaSelectionPayload(
  opportunityId: string,
  option: RecommendationOption,
  optionIndex: number,
): EwaRecommendationSelectionPayload {
  const config = optionConfig(option, optionIndex)

  return {
    opportunityId,
    selectedOption: {
      optionName: config.label.replace(`${config.shortLabel}: `, ''),
      matchScore: optionMatchScore(option) ?? undefined,
      confidence: toNumber(option.confidenceScore) ?? undefined,
      riskLevel: option.riskLevel,
      teamSize: option.selectedMemberCount ?? option.members?.length ?? 0,
    },
    selectedCandidates: (option.members ?? [])
      .filter((member) => member.opportunityRoleId && member.employeeId)
      .map((member) => {
        const memberWithOverlay = member as MemberWithOverlayReference

        return {
          opportunityOverlayId:
            memberWithOverlay.opportunityOverlayId ??
            memberWithOverlay.overlayId,
          opportunityId,
          opportunityRoleId: member.opportunityRoleId as string,
          employeeId: member.employeeId as string,
          availableFTEAtStart: toNumber(member.availableFteAtStart) ?? undefined,
          fteGap: toNumber(member.fteGap) ?? undefined,
        }
      }),
  }
}

function riskItems(option: RecommendationOption) {
  return option.risks?.filter(Boolean).slice(0, 4) ?? []
}

function nextActionItems(explanation?: OptionExplanation) {
  return explanation?.nextActions?.filter(Boolean).slice(0, 4) ?? []
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

function optionMatchScore(option: RecommendationOption) {
  const scores = (option.members ?? [])
    .map(bestMemberScore)
    .filter((score): score is number => score !== null)

  if (!scores.length) {
    return toNumber(option.confidenceScore)
  }

  const average = scores.reduce((sum, score) => sum + score, 0) / scores.length
  return Math.round(average)
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
