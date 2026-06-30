import {
  CalendarDays,
  Clock3,
  Database,
  Layers3,
  MapPin,
  ShieldCheck,
  Sparkles,
  Users,
} from 'lucide-react'
import type { RefObject } from 'react'
import type {
  RecommendationExplanationResponse,
  RecommendationOption,
} from '../../types/Recommendation'
import GenerateAiExplanationPanel from './GenerateAiExplanationPanel'
import RiskBadge from './RiskBadge'
import SkillChipGroup from './SkillChipGroup'
import {
  availabilityStatus,
  availabilityText,
  findOptionExplanation,
  formatNumber,
  formatScore,
  hasSkillEvidence,
  memberSkillEvidenceLines,
  memberSkillScorePills,
  nextActionItems,
  normalizedList,
  optionConfig,
  optionFteGap,
  readinessPill,
  readinessText,
  scorePercent,
  scoreTone,
  teamRiskItems,
  toNumber,
} from './recommendationDisplayUtils.ts'

type TeamComparisonTableProps = {
  explanation: RecommendationExplanationResponse | null
  isExplaining: boolean
  onGenerateExplanation: () => void
  options: RecommendationOption[]
  refNode: RefObject<HTMLDivElement | null>
}

function TeamComparisonTable({
  explanation,
  isExplaining,
  onGenerateExplanation,
  options,
  refNode,
}: TeamComparisonTableProps) {
  const hasExplanation = Boolean(explanation?.explanations)
  const rows = [
    {
      description: 'Confidence for this option.',
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
      description: 'Team-level required and desired skill coverage.',
      icon: <Layers3 size={15} />,
      label: 'Skill coverage',
      render: (option: RecommendationOption) => (
        <TeamSkillCoverageSummary option={option} />
      ),
    },
    {
      description: 'Countries represented by the selected members.',
      icon: <MapPin size={15} />,
      label: 'Location fit',
      render: (option: RecommendationOption) => (
        <LocationFitSummary option={option} />
      ),
    },
    {
      description: 'Member availability and start readiness.',
      icon: <CalendarDays size={15} />,
      label: 'Availability readiness',
      render: (option: RecommendationOption) => (
        <AvailabilityReadinessList option={option} />
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
      description: 'Known gaps, constraints, and missing skills.',
      icon: <ShieldCheck size={15} />,
      label: 'Key risks',
      render: (option: RecommendationOption) => (
        <ComparisonBulletList
          emptyText="No risks supplied."
          items={teamRiskItems(option, findOptionExplanation(explanation, option))}
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
          Side-by-side comparison staffing options across
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

function TeamSkillCoverageSummary({ option }: { option: RecommendationOption }) {
  const hasTeamEvidence = hasSkillEvidence(option)
  const skillScore = toNumber(option.skillCoverageScore)
  const skillPercent = scorePercent(skillScore)

  if (!hasTeamEvidence) {
    return <SkillCoverageList option={option} />
  }

  return (
    <div className="team-skill-summary">
      <div className="coverage-score-wrap">
        <span className={`coverage-score ${skillScore === null ? 'neutral' : scoreTone(skillScore, false)}`}>
          {skillScore === null ? '--' : `${formatScore(skillScore)}/100`}
        </span>
        {skillScore !== null ? (
          <span className="coverage-score-track">
            <span style={{ width: `${skillPercent}%` }} />
          </span>
        ) : null}
      </div>
      <div className="skill-group-grid">
        <SkillChipGroup
          emptyText="No required matches"
          items={option.matchedRequiredSkills}
          label="Matched required"
          tone="matched"
        />
        <SkillChipGroup
          emptyText="No desired matches"
          items={option.matchedDesiredSkills}
          label="Matched desired"
          tone="matched"
        />
      </div>
    </div>
  )
}

function LocationFitSummary({ option }: { option: RecommendationOption }) {
  const locations = normalizedList(option.locationFit)
  const score = toNumber(option.locationFitScore)

  if (!locations.length && score === null) {
    return <span className="comparison-muted">No location fit supplied.</span>
  }

  return (
    <div className="location-fit-summary">
      <div className="location-fit-head">
        <span className={`location-score ${score === null ? 'neutral' : scoreTone(score, false)}`}>
          {score === null ? '--' : `${formatScore(score)}/100`}
        </span>
      </div>
      {locations.length ? (
        <div className="location-chip-list">
          {locations.map((location) => (
            <span key={location}>{location}</span>
          ))}
        </div>
      ) : (
        <span className="comparison-muted">Countries unavailable.</span>
      )}
    </div>
  )
}

function AvailabilityReadinessList({ option }: { option: RecommendationOption }) {
  const members = (option.members ?? []).slice(0, 4)

  if (!members.length) {
    return <span className="comparison-muted">No availability supplied.</span>
  }

  return (
    <div className="availability-readiness-list">
      {members.map((member, index) => {
        const status = availabilityStatus(member)
        const fteAtStart = toNumber(member.availableFteAtStart)
        const fteGap = toNumber(member.fteGap)

        return (
          <div
            className={`availability-card ${status.tone}`}
            key={`${member.employeeId ?? 'member'}-${member.opportunityRoleId ?? index}`}
          >
            <div className="availability-card-head">
              <strong>{member.employeeName ?? 'Member'}</strong>
              <span>{status.label}</span>
            </div>
            <div className="availability-card-meta">
              <span>
                <small>Available</small>
                <strong>{availabilityText(member)}</strong>
              </span>
              <span>
                <small>FTE start</small>
                <strong>{formatNumber(fteAtStart)}</strong>
              </span>
              {fteGap !== null && fteGap > 0 ? (
                <span>
                  <small>Gap</small>
                  <strong>{formatNumber(fteGap)}</strong>
                </span>
              ) : null}
            </div>
          </div>
        )
      })}
    </div>
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

export default TeamComparisonTable
