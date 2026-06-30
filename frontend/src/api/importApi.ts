import axios, { AxiosError } from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
})

export type ExcelImportResult = {
  employeeCount: number
  employeeSkillCount: number
  availabilityCount: number
  allocationCount: number
  benchCount: number
  profileCount: number
  projectHistoryCount: number
  skillCatalogCount: number
  opportunityCount: number
  opportunityRoleCount: number
  skippedSheets: string[]
}

function normalizeImportError(error: unknown) {
  if (error instanceof AxiosError) {
    const responseData = error.response?.data
    if (typeof responseData === 'string' && responseData.trim()) {
      return new Error(responseData)
    }
    if (!error.response && error.message === 'Network Error') {
      return new Error('Failed to fetch')
    }
    return new Error('Unable to import workforce dataset')
  }
  return error
}

export const importApi = {
  async uploadWorkforceDataset(file: File): Promise<ExcelImportResult> {
    const formData = new FormData()
    formData.append('file', file)

    try {
      const response = await apiClient.post<ExcelImportResult>('/api/import/workforce-dataset/upload', formData)
      return response.data
    } catch (error) {
      throw normalizeImportError(error)
    }
  },
}
