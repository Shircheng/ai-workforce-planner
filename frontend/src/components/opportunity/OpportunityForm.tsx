import type { FormEvent } from 'react'
import type { OpportunityRequestPayload } from '../../api/opportunityApi'
import { opportunityFieldOptions, opportunityLocationHierarchy } from '../../constants/opportunityFieldOptions'
import type { OpportunityFormErrors } from '../../validation/opportunityRequestValidation'
import './OpportunityForm.css'

type OpportunityFormProps = {
  form: OpportunityRequestPayload
  errors?: OpportunityFormErrors
  isSubmitting: boolean
  onChange: (form: OpportunityRequestPayload) => void
  onSubmit: () => void
}

type SearchableOptionField =
  | 'domain'
  | 'region'
  | 'country'
  | 'city'
  | 'timezonePreference'

const locationHierarchy: Record<string, Record<string, readonly string[]>> = opportunityLocationHierarchy

function normalizeOption(value: string | undefined | null) {
  return (value ?? '').trim().toLowerCase()
}

function exactOption(options: readonly string[], value: string | undefined | null) {
  const normalized = normalizeOption(value)
  return Boolean(normalized) && options.some((option) => normalizeOption(option) === normalized)
}

function exactRecordKey<T>(record: Record<string, T>, value: string | undefined | null) {
  const normalized = normalizeOption(value)
  return Object.keys(record).find((key) => normalizeOption(key) === normalized)
}

function uniqueSorted(values: string[]) {
  return Array.from(new Set(values)).sort((first, second) => first.localeCompare(second))
}

function countriesForRegion(region: string) {
  const regionKey = exactRecordKey(locationHierarchy, region)
  if (!regionKey) return opportunityFieldOptions.country

  return uniqueSorted(Object.keys(locationHierarchy[regionKey]))
}

function citiesForLocation(region: string, country: string) {
  const regionKey = exactRecordKey(locationHierarchy, region)
  const regionKeys = regionKey ? [regionKey] : Object.keys(locationHierarchy)
  const selectedCountry = normalizeOption(country)
  const cities: string[] = []

  regionKeys.forEach((currentRegion) => {
    const countries = locationHierarchy[currentRegion]
    const countryKey = selectedCountry ? exactRecordKey(countries, country) : undefined
    if (selectedCountry && !countryKey) return

    const countryKeys = countryKey ? [countryKey] : Object.keys(countries)

    countryKeys.forEach((currentCountry) => {
      cities.push(...countries[currentCountry])
    })
  })

  return uniqueSorted(cities)
}

function locationForCountry(country: string) {
  for (const [region, countries] of Object.entries(locationHierarchy)) {
    const countryKey = exactRecordKey(countries, country)
    if (countryKey) return { region, country: countryKey }
  }

  return null
}

function locationForCity(city: string) {
  const selectedCity = normalizeOption(city)
  if (!selectedCity) return null

  for (const [region, countries] of Object.entries(locationHierarchy)) {
    for (const [country, cities] of Object.entries(countries)) {
      const cityMatch = cities.find((candidate) => normalizeOption(candidate) === selectedCity)
      if (cityMatch) return { region, country, city: cityMatch }
    }
  }

  return null
}

function OpportunityForm({ form, errors = {}, isSubmitting, onChange, onSubmit }: OpportunityFormProps) {
  const countryOptions = countriesForRegion(form.region)
  const cityOptions = citiesForLocation(form.region, form.country)

  function update<Field extends keyof OpportunityRequestPayload>(field: Field, value: OpportunityRequestPayload[Field]) {
    onChange({ ...form, [field]: value })
  }

  function updateSearchableField(field: SearchableOptionField, value: string) {
    const nextForm: OpportunityRequestPayload = { ...form, [field]: value }

    if (field === 'region') {
      const nextCountryOptions = countriesForRegion(value)
      if (nextForm.country && !exactOption(nextCountryOptions, nextForm.country)) {
        nextForm.country = ''
        nextForm.city = ''
      } else {
        const nextCityOptions = citiesForLocation(value, nextForm.country)
        if (nextForm.city && !exactOption(nextCityOptions, nextForm.city)) {
          nextForm.city = ''
        }
      }
    }

    if (field === 'country') {
      const location = locationForCountry(value)
      if (location) {
        nextForm.region = location.region
        nextForm.country = location.country
      }

      const nextCityOptions = citiesForLocation(nextForm.region, nextForm.country)
      if (nextForm.city && !exactOption(nextCityOptions, nextForm.city)) {
        nextForm.city = ''
      }
    }

    if (field === 'city') {
      const location = locationForCity(value)
      if (location) {
        nextForm.region = location.region
        nextForm.country = location.country
        nextForm.city = location.city
      }
    }

    onChange(nextForm)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit()
  }

  function fieldErrorId(field: keyof OpportunityRequestPayload) {
    return `opportunity-${field}-error`
  }

  function validationProps(field: keyof OpportunityRequestPayload) {
    return {
      'aria-invalid': errors[field] ? true : undefined,
      'aria-describedby': errors[field] ? fieldErrorId(field) : undefined,
    }
  }

  function errorMessage(field: keyof OpportunityRequestPayload) {
    const message = errors[field]
    if (!message) return null

    return <p className="field-error" id={fieldErrorId(field)}>{message}</p>
  }

  function searchableOptionField(
    field: SearchableOptionField,
    label: string,
    options: readonly string[],
    placeholder: string,
    required = false
  ) {
    const listId = `opportunity-${field}-options`

    return (
      <label className="field">
        <span>{label}{required ? ' *' : ''}</span>
        <input
          value={form[field]}
          onChange={(event) => updateSearchableField(field, event.target.value)}
          placeholder={placeholder}
          list={listId}
          required={required}
          autoComplete="off"
          {...validationProps(field)}
        />
        <datalist id={listId}>
          {options.map((option) => <option key={option} value={option} />)}
        </datalist>
        {errorMessage(field)}
      </label>
    )
  }

  return (
    <form className="opportunity-form" onSubmit={handleSubmit} noValidate>
      <label className="field field-wide">
        <span>Opportunity statement *</span>
        <textarea
          value={form.statement}
          onChange={(event) => update('statement', event.target.value)}
          placeholder="e.g. Need 4 roles for a banking onboarding modernization project in Malaysia. Skills: React, Java, Spring Boot, QA Automation. Start in 30 days."
          rows={6}
          required
          {...validationProps('statement')}
        />
        {errorMessage('statement')}
      </label>

      <label className="field field-wide">
        <span>Opportunity brief *</span>
        <textarea
          className="brief-textarea"
          value={form.opportunityBrief}
          onChange={(event) => update('opportunityBrief', event.target.value)}
          placeholder="e.g. Modernise onboarding journeys for a digital banking client, improving conversion, compliance and speed to account opening."
          rows={2}
          required
          {...validationProps('opportunityBrief')}
        />
        {errorMessage('opportunityBrief')}
      </label>

      <div className="form-grid">
        <label className="field">
          <span>Opportunity name *</span>
          <input value={form.opportunityName} onChange={(event) => update('opportunityName', event.target.value)} placeholder="e.g. Digital banking modernization" required {...validationProps('opportunityName')} />
          {errorMessage('opportunityName')}
        </label>
        <label className="field">
          <span>Client name *</span>
          <input value={form.clientName} onChange={(event) => update('clientName', event.target.value)} placeholder="e.g. ABC Bank" required {...validationProps('clientName')} />
          {errorMessage('clientName')}
        </label>
        <label className="field">
          <span>Client type</span>
          <input value={form.clientType} onChange={(event) => update('clientType', event.target.value)} placeholder="e.g. Banking / Financial Services" {...validationProps('clientType')} />
          {errorMessage('clientType')}
        </label>
        {searchableOptionField('domain', 'Domain', opportunityFieldOptions.domain, 'Search or select domain', true)}
        {searchableOptionField('region', 'Region', opportunityFieldOptions.region, 'Search or select region')}
        {searchableOptionField('country', 'Country', countryOptions, 'Search or select country', true)}
        {searchableOptionField('city', 'City', cityOptions, 'Search or select city')}
        <label className="field">
          <span>Expected start date *</span>
          <input type="date" value={form.expectedStartDate} onChange={(event) => update('expectedStartDate', event.target.value)} required {...validationProps('expectedStartDate')} />
          {errorMessage('expectedStartDate')}
        </label>
        <label className="field">
          <span>Duration weeks *</span>
          <input
            type="number"
            min="1"
            value={form.durationWeeks}
            onChange={(event) => update('durationWeeks', event.target.value === '' ? '' : Number(event.target.value))}
            placeholder="e.g. 24"
            required
            {...validationProps('durationWeeks')}
          />
          {errorMessage('durationWeeks')}
        </label>
        <label className="field">
          <span>Conversion Probability *</span>
          <input
            type="number"
            min="0"
            max="1"
            step="0.05"
            value={form.probability}
            onChange={(event) => update('probability', event.target.value === '' ? '' : Number(event.target.value))}
            placeholder="e.g. 0.65"
            required
            {...validationProps('probability')}
          />
          {errorMessage('probability')}
        </label>
        <label className="field">
          <span>Commercial priority *</span>
          <select value={form.commercialPriority} onChange={(event) => update('commercialPriority', event.target.value)} required {...validationProps('commercialPriority')}>
            <option value="">Select priority</option>
            <option>Low</option>
            <option>Medium</option>
            <option>High</option>
          </select>
          {errorMessage('commercialPriority')}
        </label>
        {searchableOptionField('timezonePreference', 'Timezone preference', opportunityFieldOptions.timezonePreference, 'Search or select timezone')}
      </div>

      <button className="primary-button" type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Parsing opportunity...' : 'Parse requirements'}
      </button>
    </form>
  )
}

export default OpportunityForm