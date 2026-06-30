import { z } from 'zod'
import type { OpportunityRequestPayload } from '../api/opportunityApi'

const requiredText = (message: string) => z.string().trim().min(1, message)

export const opportunityRequestSchema = z.object({
  statement: requiredText('Opportunity statement is required.'),
  opportunityBrief: requiredText('Opportunity brief is required.'),
  opportunityName: requiredText('Opportunity name is required.')
    .regex(/^[a-zA-Z0-9][a-zA-Z0-9 ]*$/, 'Opportunity name must be alphanumeric.'),
  clientName: requiredText('Client name is required.'),
  clientType: z.string(),
  domain: requiredText('Domain is required.'),
  region: z.string(),
  country: requiredText('Country is required.'),
  city: z.string(),
  probability: z.number({
    error: 'Probability is required.',
  }).min(0, 'Probability must be at least 0.')
    .max(1, 'Probability must be at most 1.'),
  expectedStartDate: requiredText('Expected start date is required.'),
  durationWeeks: z.number({
    error: 'Duration weeks is required.',
  }).int('Duration weeks must be a whole number.')
    .positive('Duration weeks must be greater than zero.'),
  commercialPriority: requiredText('Commercial priority is required.'),
  timezonePreference: z.string(),
}) satisfies z.ZodType<OpportunityRequestPayload>

export type OpportunityFormErrors = Partial<Record<keyof OpportunityRequestPayload, string>>

export function validateOpportunityRequest(form: OpportunityRequestPayload) {
  const parsed = opportunityRequestSchema.safeParse(form)
  if (parsed.success) {
    return {}
  }

  return parsed.error.issues.reduce<OpportunityFormErrors>((errors, issue) => {
    const field = issue.path[0] as keyof OpportunityRequestPayload | undefined
    if (field && !errors[field]) {
      errors[field] = issue.message
    }
    return errors
  }, {})
}
