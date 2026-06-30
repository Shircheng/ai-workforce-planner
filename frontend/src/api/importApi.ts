const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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

export const importApi = {
  async uploadWorkforceDataset(file: File): Promise<ExcelImportResult> {
    const formData = new FormData()
    formData.append('file', file)

    const response = await fetch(`${API_BASE_URL}/api/import/workforce-dataset/upload`, {
      method: 'POST',
      body: formData,
    })

    if (!response.ok) {
      const message = await response.text()
      throw new Error(message || 'Unable to import workforce dataset')
    }

    return response.json() as Promise<ExcelImportResult>
  },
}
