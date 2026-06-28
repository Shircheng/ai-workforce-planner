export type Bench = {
  benchRecordId: string
  employeeId: string
  employeeName: string
  benchType: string
  availabilityCategory: string
  availableFrom: string
  benchFTE: number
  benchPercent: number
  primaryDomain: string
  topSkills: string[]
  benchRisk: string
  timeOnBenchDays: number
  suggestedAction: string
  targetRoleFit: string
  ewaActionRequired: string
  isAlsoInPartialCapacityView: 'Yes' | 'No'
  recordUsage: string
}
