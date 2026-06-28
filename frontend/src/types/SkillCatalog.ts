export type SkillCatalog = {
  skillName: string
  skillCategory: string
  description: string
  relevantDepartments: string[]
  suggestedLevelScale: Record<number, string>
}
