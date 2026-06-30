import { Sparkles, X } from 'lucide-react'
import type {
  MemberExplanation,
  RecommendationOption,
  RecommendationRunMember,
} from '../../types/Recommendation'
import FitStatusPill from './FitStatusPill'
import GenerateAiExplanationPanel from './GenerateAiExplanationPanel'
import SkillChipGroup from './SkillChipGroup'
import {
  formatDate,
  formatNumber,
  formatScore,
  hasConstraint,
  hasSkillEvidence,
  initials,
  memberSkillEvidenceLines,
  notAvailable,
  optionConfig,
  scoreTone,
  toNumber,
} from './recommendationDisplayUtils.ts'

type PersonDetailDrawerProps = {
  explanation?: MemberExplanation
  isExplaining: boolean
  member: RecommendationRunMember
  onClose: () => void
  onGenerateExplanation: () => void
  option: RecommendationOption
}

function PersonDetailDrawer({
  explanation,
  isExplaining,
  member,
  onClose,
  onGenerateExplanation,
  option,
}: PersonDetailDrawerProps) {
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
        <div className="drawer-profile-copy">
          <h2>{member.employeeName ?? 'Unnamed employee'}</h2>
          <p className="drawer-role-pill">{member.roleName ?? notAvailable}</p>
          <div className="drawer-profile-tags">
            <FitStatusPill status={member.fitStatus} />
            <span>{optionConfig(option, 0).label}</span>
          </div>
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
        <MemberSkillEvidence member={member} />
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

function MemberSkillEvidence({ member }: { member: RecommendationRunMember }) {
  if (!hasSkillEvidence(member)) {
    return null
  }

  return (
    <div className="evidence-box member-skill-box">
      <div className="evidence-heading">
        <strong>Skill Coverage</strong>
        <span>{formatScore(toNumber(member.skillCoverageScore))}</span>
      </div>
      <div className="skill-group-grid member">
        <SkillChipGroup
          emptyText="No required matches"
          items={member.matchedRequiredSkills}
          label="Matched required"
          limit={4}
          tone="matched"
        />
        <SkillChipGroup
          emptyText="No required gaps"
          items={member.missingRequiredSkills}
          label="Missing required"
          limit={4}
          tone="missing"
        />
        <SkillChipGroup
          emptyText="No desired matches"
          items={member.matchedDesiredSkills}
          label="Matched desired"
          limit={4}
          tone="matched"
        />
        <SkillChipGroup
          emptyText="No desired gaps"
          items={member.missingDesiredSkills}
          label="Missing desired"
          limit={4}
          tone="missing"
        />
      </div>
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

export default PersonDetailDrawer
