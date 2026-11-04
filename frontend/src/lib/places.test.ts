import { describe, expect, it } from 'vitest'
import { ALL_INDIA, cityCounts, inPlace, placeLabel, stateCounts, UNPLACED } from './places'

const stations = [
  { city: 'Bengaluru', state: 'Karnataka' },
  { city: 'Bengaluru', state: 'Karnataka' },
  { city: 'Mysuru', state: 'Karnataka' },
  { city: 'Kochi', state: 'Kerala' },
  { city: null, state: null },
]

describe('places', () => {
  it('counts states and cities, largest first', () => {
    expect(stateCounts(stations)).toEqual([
      { name: 'Karnataka', count: 3 },
      { name: 'Kerala', count: 1 },
      { name: UNPLACED, count: 1 },
    ])
    expect(cityCounts(stations, 'Karnataka')).toEqual([
      { name: 'Bengaluru', count: 2 },
      { name: 'Mysuru', count: 1 },
    ])
  })

  it('filters by state, then city, and keeps stations without a place findable', () => {
    expect(stations.filter((s) => inPlace(s, ALL_INDIA))).toHaveLength(5)
    expect(stations.filter((s) => inPlace(s, { state: 'Karnataka', city: '' }))).toHaveLength(3)
    expect(stations.filter((s) => inPlace(s, { state: 'Karnataka', city: 'Mysuru' }))).toHaveLength(1)
    expect(stations.filter((s) => inPlace(s, { state: UNPLACED, city: '' }))).toHaveLength(1)
  })

  it('labels a place', () => {
    expect(placeLabel(ALL_INDIA)).toBe('All India')
    expect(placeLabel({ state: 'Kerala', city: '' })).toBe('Kerala')
    expect(placeLabel({ state: 'Kerala', city: 'Kochi' })).toBe('Kochi, Kerala')
  })
})
