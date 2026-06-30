import type { Opportunity } from '../types/Opportunity'
import type { OpportunityRole } from '../types/OpportunityRoles'
import type {
  RecommendationExplanationResponse,
  RecommendationRun,
} from '../types/Recommendation'

const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
)
  .replace(/\/api\/?$/, '')
  .replace(/\/$/, '')

type RequestOptions = RequestInit & {
  allowNotFound?: boolean
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(
  path: string,
  { allowNotFound, headers, ...options }: RequestOptions = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}/api${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...headers,
    },
  })

  if (allowNotFound && response.status === 404) {
    return null as T
  }

  if (!response.ok) {
    const message = await response.text()
    throw new ApiError(
      response.status,
      message || `Request failed with status ${response.status}`,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const recommendationApi = {
  getOpportunity(opportunityId: string) {
    return request<Opportunity | null>(
      `/opportunities/${encodeURIComponent(opportunityId)}`,
      { allowNotFound: true },
    )
  },

  getOpportunityRoles(opportunityId: string) {
    return request<OpportunityRole[]>(
      `/opportunities/${encodeURIComponent(opportunityId)}/roles`,
      { allowNotFound: true },
    ).then((roles) => roles ?? [])
  },

  getLatestRecommendationRun(opportunityId: string) {
    return request<RecommendationRun | null>(
      `/opportunities/${encodeURIComponent(opportunityId)}/recommendations/latest`,
      { allowNotFound: true },
    )
  },

  generateRecommendationRun(opportunityId: string) {
    return request<RecommendationRun>(
      `/opportunities/${encodeURIComponent(opportunityId)}/recommendations/generate`,
      { method: 'POST' },
    )
  },

  getRecommendationExplanation(recommendationRunId: string) {
    return request<RecommendationExplanationResponse | null>(
      `/recommendation-runs/${encodeURIComponent(recommendationRunId)}/explanations`,
      { allowNotFound: true },
    )
  },

  generateRecommendationExplanation(recommendationRunId: string) {
    return request<RecommendationExplanationResponse>(
      `/recommendation-runs/${encodeURIComponent(recommendationRunId)}/explanations/generate`,
      { method: 'POST' },
    )
  },

  prepareEwaReviewPack(recommendationRunId: string, optionType: string) {
    return request<unknown>('/ewa/review-pack', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ recommendationRunId, optionType }),
    })
  },
}
