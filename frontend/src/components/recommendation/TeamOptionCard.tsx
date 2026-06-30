import { CheckCircle2, ShieldCheck } from 'lucide-react'
import type {
  OptionExplanation,
  RecommendationOption,
  RecommendationRunMember,
} from '../../types/Recommendation'
import RiskBadge from './RiskBadge'
import {
  availabilityText,
  bestMemberScore,
  formatNumber,
  formatScore,
  hasConstraint,
  initials,
  notAvailable,
  optionConfig,
  optionFteGap,
  readinessText,
  scoreTone,
  teamRiskItems,
  toNumber,
} from './recommendationDisplayUtils.ts'

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
            <RiskBadge riskLevel={option.riskLevel} />
            <small>Risk</small>
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

export default TeamOptionCard
