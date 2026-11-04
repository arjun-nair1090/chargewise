import { ALL_INDIA, cityCounts, stateCounts, type Place } from '../lib/places'
import type { StationResponse } from '../types/api'
import { SelectField } from './Field'

/**
 * State and city pickers over the network, each option with its station count. Choosing a state resets the city;
 * the city picker lists only that state's cities.
 */
export function PlaceFilter({ value, onChange, stations }: {
  value: Place
  onChange: (place: Place) => void
  stations: StationResponse[]
}) {
  const states = stateCounts(stations)
  const cities = value.state ? cityCounts(stations, value.state) : []
  return (
    <div role="group" aria-label="Place" className="flex flex-wrap items-end gap-3">
      <div className="w-full sm:w-56">
        <SelectField
          label="State"
          value={value.state}
          onChange={(event) => onChange({ state: event.target.value, city: '' })}
        >
          <option value="">All India · {stations.length}</option>
          {states.map((state) => (
            <option key={state.name} value={state.name}>{state.name} · {state.count}</option>
          ))}
        </SelectField>
      </div>
      <div className="w-full sm:w-56">
        <SelectField
          label="City"
          value={value.city}
          disabled={!value.state}
          onChange={(event) => onChange({ ...value, city: event.target.value })}
        >
          <option value="">{value.state ? `All cities · ${cities.reduce((sum, city) => sum + city.count, 0)}` : 'Choose a state first'}</option>
          {cities.map((city) => (
            <option key={city.name} value={city.name}>{city.name} · {city.count}</option>
          ))}
        </SelectField>
      </div>
      {(value.state || value.city) && (
        <button
          type="button"
          onClick={() => onChange(ALL_INDIA)}
          className="h-10 rounded-md px-2 text-sm text-muted underline-offset-4 transition-colors duration-150 hover:text-text hover:underline"
        >
          Clear
        </button>
      )}
    </div>
  )
}
