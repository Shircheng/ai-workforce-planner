import { normalizedList } from './recommendationDisplayUtils.ts'

type SkillChipGroupProps = {
  emptyText: string
  items?: string[] | null
  label: string
  limit?: number
  tone: 'matched' | 'missing'
}

function SkillChipGroup({
  emptyText,
  items,
  label,
  limit = 5,
  tone,
}: SkillChipGroupProps) {
  const skills = normalizedList(items)
  const visibleSkills = skills.slice(0, limit)
  const hiddenCount = Math.max(skills.length - visibleSkills.length, 0)

  return (
    <div className={`skill-chip-group ${tone}`}>
      <div className="skill-chip-heading">
        <span>{label}</span>
        <small>{skills.length}</small>
      </div>
      {visibleSkills.length ? (
        <div className="skill-chip-list">
          {visibleSkills.map((skill) => (
            <span key={skill}>{skill}</span>
          ))}
          {hiddenCount ? <span>+{hiddenCount}</span> : null}
        </div>
      ) : (
        <span className="skill-chip-empty">{emptyText}</span>
      )}
    </div>
  )
}

export default SkillChipGroup
