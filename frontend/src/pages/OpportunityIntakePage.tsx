import { useEffect, useRef, useState } from 'react'
import { UploadCloud } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { employeeApi, type WorkforceDashboardResponse } from '../api/employeeApi'
import { opportunityApi, type OpportunityParseResponse, type OpportunityRequestPayload } from '../api/opportunityApi'
import PageHeader from '../components/common/PageHeader'
import OpportunityForm from '../components/opportunity/OpportunityForm'
import ParsedRequirementPanel from '../components/opportunity/ParsedRequirementPanel'
import useUnsavedChangesPrompt from '../hooks/useUnsavedChangesPrompt'
import { validateOpportunityRequest, type OpportunityFormErrors } from '../validation/opportunityRequestValidation'
import './OpportunityIntakePage.css'

type ToastState = {
  message: string
  tone: 'success' | 'warning'
}

type DatasetStatus = 'loading' | 'ready' | 'empty'

const initialForm: OpportunityRequestPayload = {
  statement: '',
  opportunityBrief: '',
  opportunityName: '',
  clientName: '',
  clientType: '',
  domain: '',
  region: '',
  country: '',
  city: '',
  probability: '',
  expectedStartDate: '',
  durationWeeks: '',
  commercialPriority: '',
  timezonePreference: '',
}

function isMeaningfulSkill(skill: string | undefined | null) {
  const normalized = (skill ?? '').trim().toLowerCase()
  return Boolean(normalized) && normalized !== 'tbd' && normalized !== 'unknown' && normalized !== 'to be confirmed'
}

function isGenericRoleName(roleName: string | undefined | null) {
  const normalized = (roleName ?? '').trim().toLowerCase()
  return !normalized || /^role\s*\d+$/.test(normalized) || normalized.startsWith('role to confirm') || normalized === 'unknown'
}

function hasMeaningfulGradePreference(gradePreference: string | undefined | null) {
  const normalized = (gradePreference ?? '').trim().toLowerCase()
  return Boolean(normalized) && normalized !== 'any' && normalized !== 'unknown' && normalized !== 'tbd' && normalized !== 'to be confirmed'
}

function rolesMissingSkillSets(result: OpportunityParseResponse) {
  return result.roles.some((role) =>
    isGenericRoleName(role.roleName)
    || !hasMeaningfulGradePreference(role.gradePreference)
    || role.requiredSkills.filter(isMeaningfulSkill).length === 0
    || role.desiredSkills.filter(isMeaningfulSkill).length === 0
  )
}

function formHasInput(form: OpportunityRequestPayload) {
  return Object.values(form).some((value) => String(value ?? '').trim().length > 0)
}

function hasDashboardData(dashboard: WorkforceDashboardResponse | null) {
  if (!dashboard) return false

  return [
    dashboard.metrics,
    dashboard.availabilityOutlook,
    dashboard.supplyByRole,
    dashboard.topSkills,
    dashboard.regions,
    dashboard.demandByDomain,
    dashboard.alerts,
  ].some((items) => items.length > 0)
}

function OpportunityIntakePage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<OpportunityRequestPayload>(initialForm)
  const [result, setResult] = useState<OpportunityParseResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<OpportunityFormErrors>({})
  const [toast, setToast] = useState<ToastState | null>(null)
  const [isInputSaved, setIsInputSaved] = useState(false)
  const [isParsing, setIsParsing] = useState(false)
  const [isGenerating, setIsGenerating] = useState(false)
  const [isGenerateConfirmOpen, setIsGenerateConfirmOpen] = useState(false)
  const [recommendationOpportunityId, setRecommendationOpportunityId] = useState<string | null>(null)
  const [shouldGenerateOnRecommendationPage, setShouldGenerateOnRecommendationPage] = useState(false)
  const [datasetStatus, setDatasetStatus] = useState<DatasetStatus>('loading')
  const pageTopRef = useRef<HTMLDivElement>(null)
  const toastTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const hasUnsavedInput = formHasInput(form) && !isInputSaved

  const unsavedNavigationPrompt = useUnsavedChangesPrompt(hasUnsavedInput, 'Input will be lost if you leave this page.')

  useEffect(() => {
    return () => {
      if (toastTimeoutRef.current) {
        clearTimeout(toastTimeoutRef.current)
      }
    }
  }, [])

  useEffect(() => {
    let isMounted = true

    employeeApi
      .getDashboard()
      .then((dashboard) => {
        if (isMounted) {
          setDatasetStatus(hasDashboardData(dashboard) ? 'ready' : 'empty')
        }
      })
      .catch(() => {
        if (isMounted) {
          setDatasetStatus('empty')
        }
      })

    return () => {
      isMounted = false
    }
  }, [])

  useEffect(() => {
    if (!recommendationOpportunityId || !isInputSaved) return

    navigate(`/opportunities/${encodeURIComponent(recommendationOpportunityId)}/recommendations`, {
      state: { generateRecommendations: shouldGenerateOnRecommendationPage },
    })
  }, [isInputSaved, navigate, recommendationOpportunityId, shouldGenerateOnRecommendationPage])

  useEffect(() => {
    if (!isGenerateConfirmOpen || isGenerating) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsGenerateConfirmOpen(false)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isGenerateConfirmOpen, isGenerating])
  useEffect(() => {
    if (!unsavedNavigationPrompt.isBlocked) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        unsavedNavigationPrompt.cancelNavigation()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [unsavedNavigationPrompt])

  function showToast(message: string, tone: ToastState['tone'] = 'success') {
    setToast({ message, tone })
    if (toastTimeoutRef.current) {
      clearTimeout(toastTimeoutRef.current)
    }
    toastTimeoutRef.current = setTimeout(() => {
      setToast(null)
    }, 3000)
  }

  function handleFormChange(nextForm: OpportunityRequestPayload) {
    setForm((currentForm) => {
      const changedFields = Object.keys(nextForm).filter((field) => {
        const typedField = field as keyof OpportunityRequestPayload
        return currentForm[typedField] !== nextForm[typedField]
      }) as Array<keyof OpportunityRequestPayload>

      if (changedFields.length > 0) {
        setIsInputSaved(false)
        setFieldErrors((currentErrors) => {
          const nextErrors = { ...currentErrors }
          changedFields.forEach((field) => {
            delete nextErrors[field]
          })
          return nextErrors
        })
      }

      return nextForm
    })
  }

  function scrollToPageTop() {
    pageTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function handleParse() {
    const validationErrors = validateOpportunityRequest(form)
    setFieldErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) {
      setResult(null)
      setError(null)
      return
    }

    setIsParsing(true)
    setError(null)
    try {
      const parsedResult = await opportunityApi.parse(form)
      const nextResult = {
        ...parsedResult,
        opportunity: {
          ...parsedResult.opportunity,
          opportunityBrief: form.opportunityBrief,
        },
      }
      setResult(nextResult)
      const isIncomplete = rolesMissingSkillSets(nextResult)
      showToast(
        isIncomplete
          ? 'Requirements parsed successfully, but incomplete requirements need review.'
          : 'Requirements parsed successfully. You may generate options now.',
        isIncomplete ? 'warning' : 'success'
      )
      requestAnimationFrame(scrollToPageTop)
    } catch (caughtError) {
      setResult(null)
      setError(caughtError instanceof Error ? caughtError.message : 'Unable to parse opportunity.')
    } finally {
      setIsParsing(false)
    }
  }

  function handleGenerateOptions() {
    if (!result || rolesMissingSkillSets(result)) return

    setIsGenerateConfirmOpen(true)
  }

  function closeGenerateConfirm() {
    if (isGenerating) return

    setIsGenerateConfirmOpen(false)
  }

  async function confirmGenerateOptions() {
    if (!result || rolesMissingSkillSets(result)) return

    setIsGenerating(true)
    setError(null)
    try {
      const storedResult = await opportunityApi.generateOptionsForRecommender(result)
      const opportunityId = storedResult.opportunity.opportunityId
      setResult(storedResult)
      setIsInputSaved(true)
      setIsGenerateConfirmOpen(false)
      setShouldGenerateOnRecommendationPage(true)
      setRecommendationOpportunityId(opportunityId)
    } catch (caughtError) {
      setIsGenerateConfirmOpen(false)
      setError(caughtError instanceof Error ? caughtError.message : 'Unable to generate recommendation options.')
      window.requestAnimationFrame(scrollToPageTop)
    } finally {
      setIsGenerating(false)
    }
  }
  return (
    <div className="opportunity-intake-page" ref={pageTopRef}>
      {toast && <div className={`toast-message ${toast.tone}`} role="status">{toast.message}</div>}
      <PageHeader title="Opportunity Intake" />
      {datasetStatus === 'loading' ? (
        <section className="opportunity-dataset-state">
          <p>Checking workforce dataset...</p>
        </section>
      ) : datasetStatus === 'empty' ? (
        <section className="opportunity-dataset-state">
          <UploadCloud aria-hidden="true" />
          <h2>Import your workforce Excel dataset from the Dashboard</h2>
          <p>
            Load the workforce dataset before creating opportunities so parsed roles,
            skills, availability, and recommendations can use the latest people data.
          </p>
          <button className="primary-button" type="button" onClick={() => navigate('/')}>
            Go to Dashboard
          </button>
        </section>
      ) : (
        <section className="opportunity-intake-layout">
          <OpportunityForm
            form={form}
            errors={fieldErrors}
            isSubmitting={isParsing}
            onChange={handleFormChange}
            onSubmit={handleParse}
          />
          <ParsedRequirementPanel
            result={result}
            error={error}
            isGenerating={isGenerating}
            isParsing={isParsing}
            onGenerateOptions={handleGenerateOptions}
          />
        </section>
      )}

      {isGenerateConfirmOpen && (
        <div className="confirm-dialog-backdrop" role="presentation" onMouseDown={closeGenerateConfirm}>
          <section
            aria-describedby="generate-confirm-description"
            aria-labelledby="generate-confirm-title"
            aria-modal="true"
            className="confirm-dialog"
            role="dialog"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="confirm-dialog-header">
              <p className="confirm-dialog-eyebrow">Confirm action</p>
              <h2 id="generate-confirm-title">Generate options?</h2>
            </div>
            <p id="generate-confirm-description">
              This will save the opportunity and parsed roles, generate recommendation options, then open the recommendation page for this opportunity.
            </p>
            <div className="confirm-dialog-actions">
              <button className="secondary-button" type="button" onClick={closeGenerateConfirm} disabled={isGenerating}>
                Cancel
              </button>
              <button className="primary-button" type="button" onClick={confirmGenerateOptions} disabled={isGenerating}>
                {isGenerating ? 'Generating...' : 'Generate and continue'}
              </button>
            </div>
          </section>
        </div>
      )}

      {unsavedNavigationPrompt.isBlocked && (
        <div className="confirm-dialog-backdrop" role="presentation" onMouseDown={unsavedNavigationPrompt.cancelNavigation}>
          <section
            aria-describedby="unsaved-navigation-description"
            aria-labelledby="unsaved-navigation-title"
            aria-modal="true"
            className="confirm-dialog"
            role="dialog"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="confirm-dialog-header">
              <p className="confirm-dialog-eyebrow">Unsaved changes</p>
              <h2 id="unsaved-navigation-title">Leave opportunity intake?</h2>
            </div>
            <p id="unsaved-navigation-description">
              {unsavedNavigationPrompt.message}
            </p>
            <div className="confirm-dialog-actions">
              <button className="secondary-button" type="button" onClick={unsavedNavigationPrompt.cancelNavigation}>
                Stay
              </button>
              <button className="primary-button" type="button" onClick={unsavedNavigationPrompt.confirmNavigation}>
                Leave page
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}

export default OpportunityIntakePage
