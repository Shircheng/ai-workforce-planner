export type EWARequest = {
  ewaRequestId: string
  opportunityId: string
  opportunityRoleId: string
  employeeId: string
  employeeName: string
  requestType: string
  ewaStatus: string
  requestedFTE: number
  proposedStartDate: string
  proposedEndDate: string
  approvalRequired: 'Yes' | 'No'
  bookingOwner: string
  blockingReason: string
  nextAction: string
  lastUpdated: string
  notes: string
  availableFTEAtStart: number
  fteGap: number
  canSplitRole: 'Yes' | 'No'
  earliestFullAvailabilityDate: string
}
