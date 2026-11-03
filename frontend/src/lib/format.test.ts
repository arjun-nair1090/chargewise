import { describe, expect, it } from 'vitest'
import { formatNanos, humanise } from './format'

describe('humanise', () => {
  it('turns an enum code into a sentence-case phrase', () => {
    expect(humanise('SESSION_DEMAND')).toBe('Session demand')
    expect(humanise('BIPARTITE_MATCHING')).toBe('Bipartite matching')
  })

  it('keeps algorithm proper nouns as their authors wrote them', () => {
    expect(humanise('EDMONDS_KARP')).toBe('Edmonds-Karp')
    expect(humanise('RANDOMIZED_QUICKSELECT')).toBe('Randomized Quickselect')
  })
})

describe('formatNanos', () => {
  it('picks the unit that keeps three significant figures readable', () => {
    expect(formatNanos(850)).toBe('850 ns')
    expect(formatNanos(27_000)).toBe('27.0 µs')
    expect(formatNanos(16_924_500)).toBe('16.9 ms')
    expect(formatNanos(1_408_920_000)).toBe('1.41 s')
  })

  it('shows a dash for a missing value', () => {
    expect(formatNanos(null)).toBe('—')
  })
})
