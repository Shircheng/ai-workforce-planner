import { useEffect, useMemo, useState } from 'react'
import { Bookmark, Heart, Trash2, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import {
  employeeApi,
  type EmployeeFilterOptions,
  type EmployeePageResponse,
  type TalentExplorerEmployee,
  type TalentFilters,
} from '../api/employeeApi'

const PAGE_SIZE = 10

const defaultFilters: TalentFilters = {
  skillSearch: '',
  availability: 'all',
  region: 'all',
  grade: 'all',
  status: 'all',
}

type SelectFilter = {
  key: keyof Omit<TalentFilters, 'skillSearch'>
  label: string
  options: {
    label: string
    value: string
  }[]
}

type SavedFilter = {
  id: string
  name: string
  filters: TalentFilters
  summary: string
}

const fallbackFilterOptions: EmployeeFilterOptions = {
  grades: ['Consultant', 'Senior Consultant', 'Lead Consultant', 'Manager'],
  regions: ['APAC', 'India', 'MENA'],
}

const uniqueSortedOptions = (values: string[]) => {
  const optionsByKey = new Map<string, string>()

  values.forEach((value) => {
    const option = value.trim()
    if (option) {
      optionsByKey.set(option.toLowerCase(), option)
    }
  })

  return Array.from(optionsByKey.values()).sort((left, right) =>
    left.localeCompare(right),
  )
}

const availabilityOptions = [
  { label: 'Availability: All', value: 'all' },
  { label: 'Availability: 30 days', value: '30' },
  { label: 'Availability: 60 days', value: '60' },
  { label: 'Availability: 90 days', value: '90' },
]

const statusOptions = [
  { label: 'Status: All', value: 'all' },
  { label: 'Status: Bench + roll-off', value: 'bench_rolloff' },
  { label: 'Status: Bench', value: 'bench' },
  { label: 'Status: Roll-off', value: 'rolloff' },
]

const createSelectFilters = (
  filterOptions: EmployeeFilterOptions,
): SelectFilter[] => [
  {
    key: 'availability',
    label: 'Availability',
    options: availabilityOptions,
  },
  {
    key: 'region',
    label: 'Region',
    options: [
      { label: 'Region: All', value: 'all' },
      ...uniqueSortedOptions(filterOptions.regions).map((region) => ({
        label: `Region: ${region}`,
        value: region,
      })),
    ],
  },
  {
    key: 'grade',
    label: 'Grade',
    options: [
      { label: 'Grade: Any', value: 'all' },
      ...filterOptions.grades.map((grade) => ({
        label: `Grade: ${grade}`,
        value: grade,
      })),
    ],
  },
  {
    key: 'status',
    label: 'Status',
    options: statusOptions,
  },
]

const skillChipClass = 'bg-teal-50 text-teal-700'

const filterLabel = (
  key: keyof Omit<TalentFilters, 'skillSearch'>,
  value: string,
  selectFilters: SelectFilter[],
) => {
  const filter = selectFilters.find((item) => item.key === key)
  return filter?.options.find((option) => option.value === value)?.label ?? value
}

const savedFilterSummary = (
  filters: TalentFilters,
  selectFilters: SelectFilter[],
) => {
  const parts = [
    filters.skillSearch ? `Skill: ${filters.skillSearch}` : null,
    filters.availability !== 'all'
      ? filterLabel('availability', filters.availability, selectFilters)
      : null,
    filters.region !== 'all'
      ? filterLabel('region', filters.region, selectFilters)
      : null,
    filters.grade !== 'all'
      ? filterLabel('grade', filters.grade, selectFilters)
      : null,
    filters.status !== 'all'
      ? filterLabel('status', filters.status, selectFilters)
      : null,
  ].filter(Boolean)

  return parts.length > 0 ? parts.join(' / ') : 'All talent profiles'
}

function TalentExplorerPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<TalentFilters>(defaultFilters)
  const [filterOptions, setFilterOptions] =
    useState<EmployeeFilterOptions>(fallbackFilterOptions)
  const [talentPage, setTalentPage] = useState<EmployeePageResponse | null>(null)
  const [favoritePeople, setFavoritePeople] = useState<TalentExplorerEmployee[]>([])
  const [savedFilters, setSavedFilters] = useState<SavedFilter[]>([])
  const [isSavedFiltersOpen, setIsSavedFiltersOpen] = useState(false)
  const [isFavoritesOpen, setIsFavoritesOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const selectFilters = useMemo(
    () => createSelectFilters(filterOptions),
    [filterOptions],
  )

  useEffect(() => {
    let isCurrentRequest = true

    employeeApi
      .getFilterOptions()
      .then((options) => {
        if (isCurrentRequest) {
          const regions = uniqueSortedOptions(options.regions)

          setFilterOptions({
            grades:
              options.grades.length > 0
                ? options.grades
                : fallbackFilterOptions.grades,
            regions:
              regions.length > 0
                ? regions
                : fallbackFilterOptions.regions,
          })
        }
      })
      .catch(() => {
        if (isCurrentRequest) {
          setFilterOptions(fallbackFilterOptions)
        }
      })

    return () => {
      isCurrentRequest = false
    }
  }, [])

  useEffect(() => {
    if (filters.region === 'all') {
      return
    }

    const availableRegions = new Set(uniqueSortedOptions(filterOptions.regions))
    if (!availableRegions.has(filters.region)) {
      setFilters((currentFilters) => ({
        ...currentFilters,
        region: 'all',
      }))
      setPage(0)
    }
  }, [filterOptions.regions, filters.region])

  useEffect(() => {
    let isCurrentRequest = true

    setIsLoading(true)
    setError(null)

    employeeApi
      .getTalentPage(page, PAGE_SIZE, filters)
      .then((response) => {
        if (isCurrentRequest) {
          setTalentPage(response)
        }
      })
      .catch((caughtError: unknown) => {
        if (isCurrentRequest) {
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : 'Unable to load talent data',
          )
        }
      })
      .finally(() => {
        if (isCurrentRequest) {
          setIsLoading(false)
        }
      })

    return () => {
      isCurrentRequest = false
    }
  }, [page, filters])

  const updateFilter = (key: keyof TalentFilters, value: string) => {
    setFilters((currentFilters) => ({
      ...currentFilters,
      [key]: value,
    }))
    setPage(0)
  }

  const favoriteIds = useMemo(
    () => new Set(favoritePeople.map((person) => person.employeeId)),
    [favoritePeople],
  )

  const toggleFavorite = (person: TalentExplorerEmployee) => {
    setFavoritePeople((currentFavorites) => {
      const isAlreadyFavorite = currentFavorites.some(
        (favorite) => favorite.employeeId === person.employeeId,
      )

      if (isAlreadyFavorite) {
        return currentFavorites.filter(
          (favorite) => favorite.employeeId !== person.employeeId,
        )
      }

      return [...currentFavorites, person]
    })
  }

  const saveCurrentFilter = () => {
    const filterKey = JSON.stringify(filters)

    setSavedFilters((currentSavedFilters) => {
      const existingFilter = currentSavedFilters.find(
        (savedFilter) => JSON.stringify(savedFilter.filters) === filterKey,
      )

      if (existingFilter) {
        return currentSavedFilters
      }

      return [
        {
          id: `${Date.now()}-${currentSavedFilters.length}`,
          name: `Saved Filter ${currentSavedFilters.length + 1}`,
          filters: { ...filters },
          summary: savedFilterSummary(filters, selectFilters),
        },
        ...currentSavedFilters,
      ]
    })

    setIsSavedFiltersOpen(true)
  }

  const applySavedFilter = (savedFilter: SavedFilter) => {
    setFilters(savedFilter.filters)
    setPage(0)
    setIsSavedFiltersOpen(false)
  }

  const removeSavedFilter = (filterId: string) => {
    setSavedFilters((currentSavedFilters) =>
      currentSavedFilters.filter((savedFilter) => savedFilter.id !== filterId),
    )
  }

  const rows = talentPage?.employees ?? []
  const totalPages = talentPage?.totalPages ?? 0
  const totalElements = talentPage?.totalElements ?? 0
  const pageStart = useMemo(() => {
    if (totalElements === 0) {
      return 0
    }
    return page * PAGE_SIZE + 1
  }, [page, totalElements])
  const pageEnd = Math.min((page + 1) * PAGE_SIZE, totalElements)

  return (
    <section className="min-h-[calc(100vh-69px)] text-slate-950">
      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="text-[34px] font-extrabold leading-tight tracking-normal text-slate-950">
            Talent Explorer
          </h1>
          <p className="mt-2 text-[18px] leading-7 text-slate-500">
            Search profiles by skill, role, grade, location, domain, and availability.
          </p>
        </div>

        <div className="flex flex-wrap gap-3 lg:pt-0">
          <div className="relative">
            <button
              type="button"
              className="relative flex h-12 items-center gap-2 rounded-lg border border-slate-300 bg-white px-5 text-base font-extrabold text-slate-950 shadow-sm transition hover:border-slate-400"
              onClick={() => setIsFavoritesOpen((isOpen) => !isOpen)}
            >
              <Heart className="h-5 w-5 text-rose-600" />
              Fav List
              <span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-[#2563eb] px-2 text-xs font-extrabold text-white">
                {favoritePeople.length}
              </span>
            </button>

            {isFavoritesOpen ? (
              <div className="absolute right-0 z-20 mt-3 w-[360px] rounded-lg border border-slate-300 bg-white shadow-xl">
                <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
                  <div className="font-extrabold text-slate-950">
                    Favorite People
                  </div>
                  <button
                    type="button"
                    className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-950"
                    onClick={() => setIsFavoritesOpen(false)}
                    aria-label="Close favorite list"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>

                <div className="max-h-[420px] overflow-y-auto p-3">
                  {favoritePeople.length === 0 ? (
                    <div className="rounded-lg bg-slate-50 px-4 py-6 text-center text-sm font-semibold text-slate-500">
                      No favorite people selected.
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {favoritePeople.map((person) => (
                        <div
                          key={person.employeeId}
                          className="rounded-lg border border-slate-200 bg-white p-3"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="truncate text-sm font-extrabold text-slate-950">
                                {person.name}
                              </div>
                              <div className="mt-1 text-sm text-slate-500">
                                {person.grade || person.department || 'Profile'} - {person.role}
                              </div>
                              <div className="mt-1 text-sm text-slate-500">
                                {person.location} - {person.availability}
                              </div>
                              <div className="mt-1 text-sm font-bold text-slate-700">
                                Fit {person.fitScore}%
                              </div>
                            </div>
                            <button
                              type="button"
                              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-rose-600 transition hover:bg-rose-50"
                              onClick={() => toggleFavorite(person)}
                              aria-label={`Remove ${person.name} from favorites`}
                            >
                              <Heart className="h-4 w-4 fill-current" />
                            </button>
                          </div>
                          {person.skills.length > 0 ? (
                            <div className="mt-3 flex flex-wrap gap-2">
                              {person.skills.slice(0, 3).map((skill) => (
                                <span
                                  key={skill}
                                  className="rounded-full bg-teal-50 px-2.5 py-1 text-xs font-semibold text-teal-700"
                                >
                                  {skill}
                                </span>
                              ))}
                            </div>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ) : null}
          </div>

          <div className="relative">
            <button
              type="button"
              className="flex h-12 items-center gap-2 rounded-lg border border-slate-300 bg-white px-5 text-base font-extrabold text-slate-950 shadow-sm transition hover:border-slate-400"
              onClick={saveCurrentFilter}
            >
              <Bookmark className="h-5 w-5 text-[#2563eb]" />
              Save Filter
              <span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-slate-100 px-2 text-xs font-extrabold text-slate-700">
                {savedFilters.length}
              </span>
            </button>

            {isSavedFiltersOpen ? (
              <div className="absolute right-0 z-20 mt-3 w-[360px] rounded-lg border border-slate-300 bg-white shadow-xl">
                <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
                  <div className="font-extrabold text-slate-950">
                    Saved Filters
                  </div>
                  <button
                    type="button"
                    className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-950"
                    onClick={() => setIsSavedFiltersOpen(false)}
                    aria-label="Close saved filters"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>

                <div className="max-h-[420px] overflow-y-auto p-3">
                  {savedFilters.length === 0 ? (
                    <div className="rounded-lg bg-slate-50 px-4 py-6 text-center text-sm font-semibold text-slate-500">
                      No saved filters yet.
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {savedFilters.map((savedFilter) => (
                        <div
                          key={savedFilter.id}
                          className="rounded-lg border border-slate-200 bg-white p-3"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <button
                              type="button"
                              className="min-w-0 flex-1 text-left"
                              onClick={() => applySavedFilter(savedFilter)}
                            >
                              <div className="truncate text-sm font-extrabold text-slate-950">
                                {savedFilter.name}
                              </div>
                              <div className="mt-1 text-sm leading-5 text-slate-500">
                                {savedFilter.summary}
                              </div>
                            </button>
                            <button
                              type="button"
                              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-orange-50 hover:text-[#9a3412]"
                              onClick={() => removeSavedFilter(savedFilter.id)}
                              aria-label={`Remove ${savedFilter.name}`}
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ) : null}
          </div>

        </div>
      </div>

      <div className="mt-7 rounded-lg border border-slate-300 bg-white px-5 py-6">
        {error ? (
          <div className="mb-4 rounded-lg bg-orange-50 px-4 py-3 text-sm font-semibold text-[#9a3412]">
            {error}. Start the backend, then refresh this page.
          </div>
        ) : null}

        <div className="grid gap-3 lg:grid-cols-[minmax(260px,2fr)_repeat(4,minmax(170px,1fr))]">
          <input
            aria-label="Search by skills"
            className="h-[52px] rounded-lg border border-slate-300 bg-white px-4 text-base text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-[#2563eb] focus:ring-2 focus:ring-blue-100"
            onChange={(event) => updateFilter('skillSearch', event.target.value)}
            placeholder="Search skills, use comma for multiple"
            type="search"
            value={filters.skillSearch}
          />
          {selectFilters.map((filter) => (
            <select
              key={filter.label}
              aria-label={filter.label}
              className="h-[52px] rounded-lg border border-slate-300 bg-white px-4 text-base text-slate-950 outline-none transition focus:border-[#2563eb] focus:ring-2 focus:ring-blue-100"
              onChange={(event) => updateFilter(filter.key, event.target.value)}
              value={filters[filter.key]}
            >
              {filter.options.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          ))}
        </div>

        <div className="mt-[18px] overflow-x-auto">
          <table className="w-full min-w-[1120px] border-collapse text-left">
            <thead className="bg-slate-50">
              <tr className="border-b border-slate-300">
                <th className="w-[19%] px-3 py-4 text-sm font-extrabold text-slate-700">
                  Person
                </th>
                <th className="w-[14%] px-3 py-4 text-sm font-extrabold text-slate-700">
                  Role
                </th>
                <th className="w-[25%] px-3 py-4 text-sm font-extrabold text-slate-700">
                  Skills
                </th>
                <th className="w-[9%] px-3 py-4 text-sm font-extrabold text-slate-700">
                  Location
                </th>
                <th className="w-[15%] px-3 py-4 text-sm font-extrabold text-slate-700">
                  Availability
                </th>
                <th className="w-[13%] px-3 py-4 text-sm font-extrabold text-slate-700">
                  Domain
                </th>
                <th className="w-[6%] px-3 py-4 text-right text-sm font-extrabold text-slate-700">
                  Fit
                </th>
                <th className="w-[5%] px-3 py-4 text-center text-sm font-extrabold text-slate-700">
                  Fav
                </th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td
                    className="px-3 py-10 text-center text-base font-semibold text-slate-500"
                    colSpan={8}
                  >
                    Loading talent data...
                  </td>
                </tr>
              ) : null}

              {!isLoading && rows.length === 0 ? (
                <tr>
                  <td
                    className="px-3 py-10 text-center text-base font-semibold text-slate-500"
                    colSpan={8}
                  >
                    No talent profiles found.
                  </td>
                </tr>
              ) : null}

              {!isLoading && rows.map((talent) => (
                <tr
                  key={talent.employeeId}
                  className="cursor-pointer border-b border-slate-300 transition hover:bg-slate-50 last:border-b"
                  onClick={() => navigate(`/people/${talent.employeeId}`)}
                >
                  <td className="px-3 py-[15px]">
                    <div className="flex items-center gap-4">
                      <div className="flex h-[47px] w-[47px] shrink-0 items-center justify-center rounded-full bg-teal-50 text-[15px] font-extrabold text-teal-700">
                        {talent.initials}
                      </div>
                      <div>
                        <div className="text-base font-extrabold leading-5 text-slate-950">
                          {talent.name}
                        </div>
                        <div className="mt-1 text-base leading-5 text-slate-500">
                          {talent.grade || talent.department || 'Profile'}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="px-3 py-[15px] text-base text-slate-950">
                    {talent.role}
                  </td>
                  <td className="px-3 py-[15px]">
                    <div className="flex flex-wrap gap-2">
                      {talent.skills.map((skill) => (
                        <span
                          key={skill}
                          className={`${skillChipClass} rounded-full px-3 py-1 text-sm font-medium`}
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-3 py-[15px] text-base text-slate-950">
                    {talent.location}
                  </td>
                  <td className="px-3 py-[15px]">
                    <span
                      className={`rounded-full px-3 py-1 text-sm font-medium ${
                        talent.availability === 'Available now'
                          ? 'bg-green-50 text-[#166534]'
                          : 'bg-orange-50 text-[#9a3412]'
                      }`}
                    >
                      {talent.availability}
                    </span>
                  </td>
                  <td className="px-3 py-[15px] text-base text-slate-950">
                    {talent.domain}
                  </td>
                  <td className="px-3 py-[15px] text-right text-base font-extrabold text-slate-950">
                    {talent.fitScore}%
                  </td>
                  <td className="px-3 py-[15px] text-center">
                    <button
                      type="button"
                      className={`inline-flex h-10 w-10 items-center justify-center rounded-lg transition ${
                        favoriteIds.has(talent.employeeId)
                          ? 'bg-rose-50 text-rose-600'
                          : 'text-slate-400 hover:bg-slate-100 hover:text-rose-600'
                      }`}
                      onClick={(event) => {
                        event.stopPropagation()
                        toggleFavorite(talent)
                      }}
                      aria-label={`${favoriteIds.has(talent.employeeId) ? 'Remove' : 'Add'} ${talent.name} favorite`}
                      aria-pressed={favoriteIds.has(talent.employeeId)}
                    >
                      <Heart
                        className={`h-5 w-5 ${
                          favoriteIds.has(talent.employeeId) ? 'fill-current' : ''
                        }`}
                      />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-5 flex flex-col gap-3 border-t border-slate-200 pt-4 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between">
          <div>
            Showing <span className="font-bold text-slate-900">{pageStart}</span>-
            <span className="font-bold text-slate-900">{pageEnd}</span> of{' '}
            <span className="font-bold text-slate-900">{totalElements}</span> matched people
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="h-10 rounded-lg border border-slate-300 bg-white px-4 font-bold text-slate-950 disabled:cursor-not-allowed disabled:opacity-45"
              disabled={isLoading || page === 0}
              onClick={() => setPage((currentPage) => Math.max(0, currentPage - 1))}
            >
              Previous
            </button>
            <div className="min-w-28 text-center font-bold text-slate-900">
              Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
            </div>
            <button
              type="button"
              className="h-10 rounded-lg bg-[#2563eb] px-4 font-bold text-white disabled:cursor-not-allowed disabled:opacity-45"
              disabled={isLoading || totalPages === 0 || page + 1 >= totalPages}
              onClick={() =>
                setPage((currentPage) =>
                  Math.min(Math.max(totalPages - 1, 0), currentPage + 1),
                )
              }
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </section>
  )
}

export default TalentExplorerPage
