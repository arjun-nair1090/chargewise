import type { StationResponse } from '../types/api'

/** States and union territories of India, for the station form. */
export const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh',
  'Chhattisgarh', 'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana',
  'Himachal Pradesh', 'Jammu and Kashmir', 'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep',
  'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Puducherry', 'Punjab',
  'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
] as const

/** A view filter over the network: '' means every state, or every city in the state. */
export type Place = { state: string; city: string }

export const ALL_INDIA: Place = { state: '', city: '' }

/** Stations created without a place still need a bucket to be found in. */
export const UNPLACED = 'Not set'

type Located = Pick<StationResponse, 'city' | 'state'>

export function stateOf(station: Located): string {
  return station.state ?? UNPLACED
}

export function cityOf(station: Located): string {
  return station.city ?? UNPLACED
}

export function inPlace(station: Located, place: Place): boolean {
  if (place.state && stateOf(station) !== place.state) {
    return false
  }
  return !place.city || cityOf(station) === place.city
}

export type PlaceCount = { name: string; count: number }

/** Names with their station counts, largest first, then alphabetical. */
function tally(names: string[]): PlaceCount[] {
  const counts = new Map<string, number>()
  for (const name of names) {
    counts.set(name, (counts.get(name) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name))
}

export function stateCounts(stations: Located[]): PlaceCount[] {
  return tally(stations.map(stateOf))
}

export function cityCounts(stations: Located[], state: string): PlaceCount[] {
  return tally(stations.filter((station) => stateOf(station) === state).map(cityOf))
}

export function placeLabel(place: Place): string {
  if (place.city) {
    return `${place.city}, ${place.state}`
  }
  return place.state || 'All India'
}
