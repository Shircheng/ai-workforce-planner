import type { OpportunityParseResponse } from '../../api/opportunityApi'
import type { OpportunityRole } from '../../types/OpportunityRoles'
import './ParsedRequirementPanel.css'

type ParsedRequirementPanelProps = {
  result: OpportunityParseResponse | null
  error: string | null
  isGenerating: boolean
  isParsing: boolean
  onGenerateOptions: () => void
}

type ConstraintChip = {
  label: string
  kind: 'domain' | 'location' | 'timing' | 'flexibility'
}

function unique(values: string[]) {
  return Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)))
}

function normalize(value: string | undefined | null) {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase()
}

function meaningful(value: string | undefined | null) {
  const normalized = normalize(value)
  return Boolean(normalized)
    && !/^\d+(?:\.\d+)?$/.test(normalized)
    && normalized !== 'tbd'
    && normalized !== 'unknown'
    && normalized !== 'to be confirmed'
    && normalized !== 'confirm with requester'
}

const knownDomains = [
  'Banking',
  'Education',
  'Energy',
  'Financial Services',
  'Healthcare',
  'Insurance',
  'Internal Platforms',
  'Legacy Platforms',
  'Logistics',
  'Media',
  'Payments',
  'Public Sector',
  'Retail',
  'Telecommunications',
  'Travel',
]

function formatDomain(value: string | undefined | null) {
  if (!meaningful(value)) return ''

  const cleaned = (value ?? '')
    .trim()
    .replace(/\.+$/g, '')
    .replace(/\s+domain\s+experience\s+(required|preferred|needed)$/i, '')
    .replace(/\s+domain\s+experience$/i, '')
    .replace(/\s+experience\s+(required|preferred|needed)$/i, '')
    .replace(/\s+(required|preferred|needed)$/i, '')
    .replace(/\s+domain\s+(required|preferred|needed)$/i, '')
    .replace(/\s+domain$/i, '')
    .trim()

  const normalized = normalize(cleaned)
  const matchedDomain = knownDomains.find((domain) => {
    const domainKey = normalize(domain)
    return normalized === domainKey || normalized.startsWith(`${domainKey} `) || normalized.includes(`${domainKey} domain`) || normalized.includes(`${domainKey} experience`)
  })

  return matchedDomain ?? cleaned
}

function requiredSkillsFromRoles(roles: OpportunityRole[]) {
  return unique(roles.flatMap((role) => role.requiredSkills).filter(meaningful))
}

function desiredSkillsFromRoles(roles: OpportunityRole[]) {
  const requiredSkillKeys = new Set(requiredSkillsFromRoles(roles).map(normalize))
  return unique(roles.flatMap((role) => role.desiredSkills).filter(meaningful))
    .filter((skill) => !requiredSkillKeys.has(normalize(skill)))
}

function isGenericRoleName(roleName: string | undefined | null) {
  const normalized = normalize(roleName)
  return !meaningful(roleName) || /^role\s*\d+$/.test(normalized) || normalized.startsWith('role to confirm')
}

function hasMeaningfulGradePreference(gradePreference: string | undefined | null) {
  const normalized = normalize(gradePreference)
  return meaningful(gradePreference) && normalized !== 'any'
}

function roleHasIncompleteRequirement(role: OpportunityRole) {
  return isGenericRoleName(role.roleName)
    || !hasMeaningfulGradePreference(role.gradePreference)
    || role.requiredSkills.filter(meaningful).length === 0
    || role.desiredSkills.filter(meaningful).length === 0
}

function roleRequirementWarningMessages(role: OpportunityRole) {
  const messages: string[] = []
  const missingRequiredSkills = role.requiredSkills.filter(meaningful).length === 0
  const missingDesiredSkills = role.desiredSkills.filter(meaningful).length === 0

  if (isGenericRoleName(role.roleName)) {
    messages.push('Role name needed before options can be generated.')
  }
  if (!hasMeaningfulGradePreference(role.gradePreference)) {
    messages.push('Grade preference needed before options can be generated.')
  }
  if (missingRequiredSkills && missingDesiredSkills) {
    messages.push('Required and desired skills needed before options can be generated.')
  } else if (missingRequiredSkills) {
    messages.push('Required skills needed before options can be generated.')
  } else if (missingDesiredSkills) {
    messages.push('Desired skills needed before options can be generated.')
  }
  return messages
}

function isRoleRequirementValidationIssue(field: string) {
  return field.includes('.roleName')
    || field.includes('.gradePreference')
    || field.includes('.requiredSkills')
    || field.includes('.desiredSkills')
    || field === 'roles.requiredSkills'
}
function addConstraint(chips: ConstraintChip[], seen: Set<string>, label: string, kind: ConstraintChip['kind']) {
  const key = normalize(label)
  if (!key || seen.has(key)) return
  seen.add(key)
  chips.push({ label, kind })
}

function formatGradePreference(gradePreference: string) {
  const value = gradePreference.trim()
  const normalized = normalize(value)
  if (!meaningful(value) || normalized === 'any') return ''
  if (normalized.includes('senior manager')) return 'Senior Manager level preferred'
  if (normalized.includes('principal consultant')) return 'Principal Consultant level preferred'
  if (normalized.includes('lead consultant')) return 'Lead Consultant level preferred'
  if (normalized.includes('senior consultant')) return 'Senior Consultant level preferred'
  if (normalized.includes('associate consultant')) return 'Associate Consultant level preferred'
  if (normalized.includes('manager')) return 'Manager level preferred'
  if (normalized === 'lead' || normalized.includes('lead level')) return 'Lead level preferred'
  if (normalized === 'senior' || normalized === 'sr' || normalized.includes('senior level')) return 'Senior level preferred'
  if (normalized === 'junior' || normalized === 'jr' || normalized.includes('junior level')) return 'Junior level preferred'
  if (normalized === 'mid' || normalized === 'middle' || normalized.includes('mid level')) return 'Mid level preferred'
  if (normalized === 'intermediate' || normalized.includes('intermediate level')) return 'Intermediate level preferred'
  if (normalized.includes('consultant')) return 'Consultant level preferred'
  return `${value} preferred`
}

function cleanFlexibilityNote(note: string | undefined | null) {
  if (!meaningful(note)) return ''

  const parts = (note ?? '')
    .split(/[.;]/)
    .map((part) => part.trim())
    .filter((part) => {
      const normalized = normalize(part)
      return meaningful(part)
        && !normalized.includes('senior level preferred')
        && !normalized.includes('can combine candidates')
        && !normalized.includes('combined candidates')
        && !normalized.includes('staffed by combined')
        && !normalized.includes('can be split')
        && !normalized.includes('can split')
        && !normalized.includes('split across')
        && !normalized.includes('multiple candidates')
        && !normalized.includes('minimum individual allocation')
        && !normalized.includes('minimum individual fte')
        && !normalized.includes('must be available by start date')
        && !normalized.includes('available by the start date')
        && !normalized.includes('available by start')
        && !normalized.includes('no specific location')
        && !normalized.includes('no location stated')
    })

  return unique(parts).join('; ')
}

function constraintsFromResult(result: OpportunityParseResponse) {
  const { opportunity, roles } = result
  const chips: ConstraintChip[] = []
  const seen = new Set<string>()

  const opportunityDomain = formatDomain(opportunity.domain)
  if (meaningful(opportunityDomain)) {
    addConstraint(chips, seen, opportunityDomain, 'domain')
  }

  unique(roles.map((role) => role.locationPreference).filter(meaningful))
    .forEach((location) => addConstraint(chips, seen, `${location} location`, 'location'))

  if (meaningful(opportunity.timezonePreference)) {
    addConstraint(chips, seen, `${opportunity.timezonePreference} timezone`, 'timing')
  }

  unique(roles.map((role) => formatGradePreference(role.gradePreference)).filter(meaningful))
    .forEach((grade) => addConstraint(chips, seen, grade, 'flexibility'))

  unique(roles.map((role) => cleanFlexibilityNote(role.flexibilityNotes)).filter(meaningful))
    .forEach((note) => addConstraint(chips, seen, note, 'flexibility'))

  return chips
}

function ParsedRequirementOverview({ result }: { result: OpportunityParseResponse }) {
  const roleNames = unique(result.roles.map((role) => role.roleName).filter(meaningful))
  const requiredSkills = requiredSkillsFromRoles(result.roles)
  const desiredSkills = desiredSkillsFromRoles(result.roles)
  const constraints = constraintsFromResult(result)

  return (
    <div className="requirement-overview">
      <h3>Parsed Requirement</h3>
      <div className="overview-section">
        <h4>Roles</h4>
        <div className="overview-chip-row">
          {roleNames.map((role) => <span className="overview-chip role-chip" key={role}>{role}</span>)}
        </div>
      </div>
      <div className="overview-section">
        <h4>Skills</h4>
        <div className="overview-chip-row">
          {requiredSkills.map((skill) => <span className="overview-chip required-skill-chip" key={`required-${skill}`}>{skill}</span>)}
          {desiredSkills.map((skill) => <span className="overview-chip desired-skill-chip" key={`desired-${skill}`}>{skill}</span>)}
        </div>
      </div>
      <div className="overview-section">
        <h4>Constraints</h4>
        <div className="overview-chip-row constraint-chip-row">
          {constraints.map((constraint) => (
            <span className={`overview-chip constraint-chip ${constraint.kind}`} key={`${constraint.kind}-${constraint.label}`}>
              {constraint.label}
            </span>
          ))}
        </div>
      </div>
    </div>
  )
}

function ParsedRequirementPanel({ result, error, isGenerating, isParsing, onGenerateOptions }: ParsedRequirementPanelProps) {
  if (isParsing) {
    return (
      <section className="parsed-panel loading-panel" aria-live="polite" role="status">
        <p className="eyebrow">Parsed requirements</p>
        <div className="preview-loading-header">
          <span className="preview-spinner" aria-hidden="true" />
          <div>
            <h2>Parsing requirements...</h2>
            <p>Reading the opportunity statement and preparing the validated preview.</p>
          </div>
        </div>
        <div className="preview-skeleton-list" aria-hidden="true">
          <span className="preview-skeleton wide" />
          <span className="preview-skeleton" />
          <span className="preview-skeleton short" />
        </div>
      </section>
    )
  }
  if (error) {
    return (
      <section className="parsed-panel error-panel">
        <p className="eyebrow">Validation failed</p>
        <p>{error}</p>
      </section>
    )
  }

  if (!result) {
    return (
      <section className="parsed-panel empty-panel">
        <p className="eyebrow">Parsed requirements</p>
        <h2 className="empty-panel-title">Submit an opportunity to see roles</h2>
      </section>
    )
  }

  const isStored = result.parseSource === 'stored'
  const hasSkillBlockers = result.roles.some(roleHasIncompleteRequirement)
  const generateBlockedMessage = hasSkillBlockers ? 'Unable to generate options due to incomplete requirements provided.' : ''
  const visibleValidationIssues = result.validationIssues.filter((issue) => !isRoleRequirementValidationIssue(issue.field))

  return (
    <section className="parsed-panel">
      <div className="parsed-header">
        <div>
          <p className="eyebrow">{isStored ? 'Stored opportunity' : 'Validated preview'}</p>
          <h2>{result.opportunity.opportunityName}</h2>
          <p>{result.opportunity.clientName} - {formatDomain(result.opportunity.domain)}</p>
        </div>
      </div>

      {!isStored && (
        <div className="generate-action" title={generateBlockedMessage || undefined}>
          <button
            className="primary-button"
            type="button"
            onClick={onGenerateOptions}
            disabled={isGenerating || hasSkillBlockers}
            title={generateBlockedMessage || undefined}
            aria-describedby={generateBlockedMessage ? 'generate-options-blocked-message' : undefined}
          >
            {isGenerating ? 'Generating...' : 'Generate options for recommender'}
          </button>
          {generateBlockedMessage && (
            <p className="generate-blocked-message" id="generate-options-blocked-message">{generateBlockedMessage}</p>
          )}
        </div>
      )}


      {result.opportunity.opportunityBrief && (
        <div className="brief-box">
          <strong>Opportunity brief</strong>
          <p>{result.opportunity.opportunityBrief}</p>
        </div>
      )}
      {visibleValidationIssues.length > 0 && (
        <div className="validation-list">
          {visibleValidationIssues.map((issue) => (
            <div className={`validation-item ${issue.severity}`} key={`${issue.field}-${issue.message}`}>
              <strong>{issue.severity === 'warning' ? 'Warning' : 'Error'}</strong>
              <span>{issue.field}: {issue.message}</span>
            </div>
          ))}
        </div>
      )}

      <ParsedRequirementOverview result={result} />

      <div className="role-list">
        {result.roles.map((role) => {
          const cleanedNote = cleanFlexibilityNote(role.flexibilityNotes)
          const skillWarnings = roleRequirementWarningMessages(role)

          return (
            <article className="role-card" key={role.opportunityRoleId}>
              <div className="role-card-header">
                <div>
                  <h3>{role.roleName}</h3>
                  <p>{role.disciplineOrDepartment} - {role.gradePreference}</p>
                </div>
                <span>{role.fteRequired} FTE</span>
              </div>
              <div className="tag-row">
                {role.requiredSkills.map((skill) => (
                  <span className="tag required" key={skill}>{skill}</span>
                ))}
                {role.desiredSkills.map((skill) => (
                  <span className="tag" key={skill}>{skill}</span>
                ))}
              </div>
              <dl className="role-meta">
                <div>
                  <dt>Domain</dt>
                  <dd>{formatDomain(role.domainExperienceRequired)}</dd>
                </div>
                <div>
                  <dt>Location</dt>
                  <dd>{role.locationPreference}</dd>
                </div>
                <div>
                  <dt>Start</dt>
                  <dd>{role.startDate}</dd>
                </div>
                <div>
                  <dt>Duration</dt>
                  <dd>{role.durationWeeks} weeks</dd>
                </div>
                <div>
                  <dt>Priority</dt>
                  <dd>{role.priority}</dd>
                </div>
                <div>
                  <dt>Can split</dt>
                  <dd>{role.canCombineCandidates}</dd>
                </div>
              </dl>
              {cleanedNote && <p className="role-notes">{cleanedNote}</p>}
              {skillWarnings.length > 0 && (
                <div className="role-warning-list" role="alert">
                  {skillWarnings.map((message) => <p key={message}>{message}</p>)}
                </div>
              )}
            </article>
          )
        })}
      </div>
    </section>
  )
}

export default ParsedRequirementPanel
