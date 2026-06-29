import { useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import {
  ewaApi,
  type EwaRecommendedPerson,
  type EwaHydratedCandidate,
  type EwaHydratedSelectionData,
  type EwaRecommendationSelectionPayload,
  type EwaReviewPack,
  type EwaSelectedCandidateReference,
  type EwaTeamOptionSummary,
} from '../api/ewaApi'
import EwaStatusPanel from '../components/ewa/EwaStatusPanel'
import EwaSummary from '../components/ewa/EwaSummary'
import '../components/ewa/EwaReviewPack.css'

type EwaRouteState = {
  ewaSelectionPayload?: EwaRecommendationSelectionPayload
  recommendationSelection?: EwaRecommendationSelectionPayload
  opportunityId?: string
  opportunityOverlayId?: string
  overlayId?: string
  opportunityRoleId?: string
  employeeId?: string
  availableFTEAtStart?: number
  fteGap?: number
  selectedOption?: EwaTeamOptionSummary
  selectedCandidates?: EwaSelectedCandidateReference[]
}

function EwaReviewPackPage() {
  const location = useLocation()
  const { id: routeOpportunityId } = useParams()
  const routeState = location.state as EwaRouteState | null
  const [pack, setPack] = useState<EwaReviewPack | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [submitMessage, setSubmitMessage] = useState('')
  const [regionalPlannerNotes, setRegionalPlannerNotes] = useState('')

  useEffect(() => {
    let isActive = true

    const integrationPayload =
      getIntegrationPayload(routeState, routeOpportunityId) ??
      getAssumedRecommendationPayload()

    ewaApi
      .hydrateRecommendationSelection(integrationPayload)
      .then((selectionData) => {
        if (isActive) {
          setError('')
          setSubmitMessage('')
          setPack(buildHydratedSelectionPack(selectionData))
        }
      })
      .catch((requestError) => {
        if (isActive) {
          setPack(null)
          setSubmitMessage('')
          setError(
            requestError instanceof Error
              ? requestError.message
              : 'Unable to load selected recommendation details.',
          )
        }
      })

    return () => {
      isActive = false
    }
  }, [routeOpportunityId, routeState])

  async function submitToEwa() {
    if (!pack) return

    const peopleToSubmit = (pack.recommendedPeople ?? []).filter(
      (person) => person.employeeId && person.opportunityRoleId,
    )

    if (!pack.opportunityId || peopleToSubmit.length === 0) {
      setError('Missing opportunity, employee, or opportunity role for EWA submission.')
      return
    }

    setIsSubmitting(true)
    setError('')
    setSubmitMessage('')

    try {
      const submittedRequests = await Promise.all(
        peopleToSubmit.map((person) =>
          ewaApi.submitEwaRequest({
            opportunityId: pack.opportunityId,
            opportunityRoleId: person.opportunityRoleId as string,
            employeeId: person.employeeId as string,
            availableFTEAtStart: person.availableFTEAtStart,
            fteGap: person.fteGap,
            notes: regionalPlannerNotes,
          }),
        ),
      )

      setPack((current) => {
        if (!current) return current
        return {
          ...current,
          status: 'SUBMITTED_TO_EWA',
        }
      })
      setSubmitMessage(`${submittedRequests.length} EWA request(s) submitted successfully.`)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Unable to submit EWA request.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="ewa-page">
      <header className="ewa-title-bar">
        <div>
          <h1>EWA Review Pack</h1>
          <span>Selected team handover for approval and booking</span>
        </div>
      </header>

      {error ? <div className="ewa-alert">{error}</div> : null}
      {submitMessage ? <div className="ewa-success">{submitMessage}</div> : null}

      {pack ? (
        <section className="ewa-layout-grid">
          <main className="ewa-content-stack">
            <EwaSummary
              opportunity={pack.opportunitySummary}
              teamOption={pack.selectedTeamOption}
            />

            <RecommendedPeopleTable people={pack.recommendedPeople ?? []} />
          </main>

          <aside className="ewa-side-stack">
            <EwaStatusPanel
              status={pack.status}
              generatedAt={pack.generatedAt}
              notes={regionalPlannerNotes}
              isSubmitting={isSubmitting}
              canSubmit={canSubmitPack(pack)}
              onExportPdf={() => window.print()}
              onSubmitToEwa={() => void submitToEwa()}
              onNotesChange={setRegionalPlannerNotes}
            />
          </aside>
        </section>
      ) : null}
    </div>
  )
}

function getIntegrationPayload(
  routeState: EwaRouteState | null,
  routeOpportunityId?: string,
): EwaRecommendationSelectionPayload | undefined {
  if (!routeState) return undefined
  if (routeState.ewaSelectionPayload) {
    return normalizeSelectionPayload(routeState.ewaSelectionPayload, routeOpportunityId)
  }
  if (routeState.recommendationSelection) {
    return normalizeSelectionPayload(routeState.recommendationSelection, routeOpportunityId)
  }
  return normalizeSelectionPayload(routeState, routeOpportunityId)
}

function getAssumedRecommendationPayload(): EwaRecommendationSelectionPayload {
  return {
    ...assumedRecommendationPayload,
    selectedCandidates: assumedRecommendationPayload.selectedCandidates.map((candidate) => ({
      ...candidate,
      opportunityId: assumedRecommendationPayload.opportunityId,
    })),
  }
}

function normalizeSelectionPayload(
  value: Partial<EwaRecommendationSelectionPayload> & Partial<EwaSelectedCandidateReference>,
  routeOpportunityId?: string,
): EwaRecommendationSelectionPayload | undefined {
  const opportunityId = value.opportunityId ?? routeOpportunityId
  if (!opportunityId) return undefined

  if (Array.isArray(value.selectedCandidates) && value.selectedCandidates.length > 0) {
    const selectedCandidates = value.selectedCandidates
      .filter(isSelectedCandidateReference)
      .map((candidate) => ({
        ...candidate,
        opportunityId: candidate.opportunityId ?? opportunityId,
      }))

    if (selectedCandidates.length > 0) {
      return {
        opportunityId,
        selectedOption: value.selectedOption,
        selectedCandidates,
      }
    }
  }

  if (value.opportunityRoleId && value.employeeId) {
    return {
      opportunityId,
      selectedOption: value.selectedOption,
      selectedCandidates: [
        {
          opportunityId,
          opportunityOverlayId: value.opportunityOverlayId,
          overlayId: value.overlayId,
          opportunityRoleId: value.opportunityRoleId,
          employeeId: value.employeeId,
          availableFTEAtStart: value.availableFTEAtStart,
          fteGap: value.fteGap,
        },
      ],
    }
  }

  return undefined
}

function isSelectedCandidateReference(
  value: Partial<EwaSelectedCandidateReference>,
): value is EwaSelectedCandidateReference {
  return Boolean(value.opportunityRoleId && value.employeeId)
}

function RecommendedPeopleTable({ people }: { people: EwaRecommendedPerson[] }) {
  return (
    <section className="ewa-card">
      <div className="ewa-section-header">
        <h2>Selected Overlay Candidates</h2>
        <span>{people.length} people</span>
      </div>

      <div className="ewa-table-wrap">
        <table className="ewa-table">
          <thead>
            <tr>
              <th>Role</th>
              <th>Employee</th>
              <th>Scores</th>
              <th>FTE</th>
              <th>Skill Evidence</th>
            </tr>
          </thead>
          <tbody>
            {people.map((person) => (
              <tr key={`${person.employeeId}-${person.opportunityRoleId ?? ''}`}>
                <td>
                  {person.role || '-'}
                  <small>{person.opportunityRoleId}</small>
                </td>
                <td>
                  <strong>{person.employeeName || person.employeeId || '-'}</strong>
                  <small>{person.employeeId}</small>
                </td>
                <td>
                  <div className="ewa-score-stack">
                    <span>Overall {formatPercent(person.overallStaffingScore ?? person.matchScore)}</span>
                    <small>Capability {formatPercent(person.capabilityFitScore)}</small>
                    <small>Availability {formatPercent(person.availabilityFitScore)}</small>
                  </div>
                </td>
                <td>
                  <strong>{formatNumber(person.availableFTEAtStart)} available</strong>
                  <small>{formatNumber(person.fteGap)} FTE gap</small>
                </td>
                <td>
                  <strong>
                    Required {person.requiredSkillsMatched ?? 0}/{person.requiredSkillsTotal ?? 0}
                  </strong>
                  <small>
                    Desired {person.desiredSkillsMatched ?? 0}/{person.desiredSkillsTotal ?? 0}
                  </small>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function formatPercent(value?: number) {
  if (value === undefined || value === null) return '-'
  return `${value}%`
}

function formatNumber(value?: number) {
  if (value === undefined || value === null) return '0'
  return Number(value).toLocaleString(undefined, {
    maximumFractionDigits: 2,
  })
}

function canSubmitPack(pack: EwaReviewPack) {
  return Boolean(
    pack.opportunityId &&
      (pack.recommendedPeople ?? []).some(
        (person) => person.employeeId && person.opportunityRoleId,
      ),
  )
}

function buildReviewPack(payload: {
  opportunity: EwaHydratedSelectionData['opportunity']
  selectedOption: EwaTeamOptionSummary
  recommendedPeople: EwaRecommendedPerson[]
}): EwaReviewPack {
  const people = payload.recommendedPeople
  return {
    opportunityId: payload.opportunity.opportunityId,
    status: 'READY_FOR_EWA_REVIEW',
    generatedAt: new Date().toISOString(),
    opportunitySummary: payload.opportunity,
    selectedTeamOption: {
      ...payload.selectedOption,
      teamSize: people.length,
      matchScore: payload.selectedOption.matchScore ?? average(people.map((row) => row.overallStaffingScore)),
      confidence: payload.selectedOption.confidence ?? average(people.map((row) => row.availabilityFitScore)),
    },
    recommendedPeople: people,
  }
}

function buildHydratedSelectionPack(selectionData: EwaHydratedSelectionData): EwaReviewPack {
  const recommendedPeople = selectionData.selectedCandidates.map(buildRecommendedPerson)

  return buildReviewPack({
    opportunity: selectionData.opportunity,
    selectedOption: {
      optionName: selectionData.selectedOption?.optionName ?? 'Selected recommendation option',
      matchScore:
        selectionData.selectedOption?.matchScore ??
        average(recommendedPeople.map((person) => person.overallStaffingScore)),
      confidence:
        selectionData.selectedOption?.confidence ??
        average(recommendedPeople.map((person) => person.availabilityFitScore)),
      riskLevel: selectionData.selectedOption?.riskLevel ?? selectionData.opportunity.deliveryRisk,
      teamSize: recommendedPeople.length,
    },
    recommendedPeople,
  })
}

function buildRecommendedPerson(candidate: EwaHydratedCandidate): EwaRecommendedPerson {
  const overlay =
    candidate.overlay?.employeeId === candidate.employeeId ? candidate.overlay : undefined
  const role = candidate.role

  return {
    opportunityRoleId: candidate.opportunityRoleId,
    employeeId: candidate.employeeId,
    employeeName: candidate.employee?.employeeName ?? overlay?.employeeName ?? candidate.employeeId,
    role: role?.roleName,
    matchScore: overlay?.matchScore,
    capabilityFitScore: overlay?.capabilityFitScore ?? 0,
    availabilityFitScore: overlay?.availabilityFitScore ?? 0,
    overallStaffingScore: overlay?.overallStaffingScore ?? overlay?.matchScore ?? 0,
    availableFTEAtStart: candidate.availableFTEAtStart ?? overlay?.availableFTEAtStart ?? 0,
    fteGap: candidate.fteGap ?? overlay?.fteGap ?? 0,
    requiredSkillsMatched: overlay?.requiredSkillsMatched ?? 0,
    requiredSkillsTotal: overlay?.requiredSkillsTotal ?? role?.requiredSkills?.length ?? 0,
    desiredSkillsMatched: overlay?.desiredSkillsMatched ?? 0,
    desiredSkillsTotal: overlay?.desiredSkillsTotal ?? role?.desiredSkills?.length ?? 0,
  }
}

function average(values: Array<number | undefined>) {
  const usableValues = values.filter(
    (value): value is number => typeof value === 'number' && Number.isFinite(value),
  )
  if (usableValues.length === 0) return undefined
  return Math.round(
    usableValues.reduce((total, value) => total + value, 0) / usableValues.length,
  )
}

const assumedRecommendationPayload: EwaRecommendationSelectionPayload = {
  opportunityId: 'OPP-001',
  selectedOption: {
    optionName: 'Balanced low-risk team',
    matchScore: 78,
    confidence: 100,
    riskLevel: 'Medium',
  },
  selectedCandidates: [
    {
      opportunityOverlayId: 'OVR-00002',
      opportunityRoleId: 'OPR-0001',
      employeeId: 'EMP-463', // Noah Wilson
      availableFTEAtStart: 0.5,
      fteGap: 0,
    },
    {
      opportunityOverlayId: 'OVR-00008',
      opportunityRoleId: 'OPR-0003',
      employeeId: 'EMP-086', // Lucas Walker
      availableFTEAtStart: 1,
      fteGap: 0,
    },
    {
      opportunityOverlayId: 'OVR-00010',
      opportunityRoleId: 'OPR-0004',
      employeeId: 'EMP-398', // Ishaan Kapoor
      availableFTEAtStart: 1,
      fteGap: 0,
    },
  ],
}

export default EwaReviewPackPage
