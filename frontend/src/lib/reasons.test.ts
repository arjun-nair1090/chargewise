import { describe, expect, it } from 'vitest'
import { STATUS_REASONS } from '../types/api'
import { describeReason } from './reasons'

describe('describeReason', () => {
  it('gives every status reason a label and a full sentence', () => {
    for (const reason of STATUS_REASONS) {
      const text = describeReason(reason)
      expect(text.label.length, reason).toBeGreaterThan(0)
      expect(text.sentence, reason).toMatch(/^[A-Z].*\.$/)
      expect(text.label, reason).not.toContain('_')
    }
  })

  it('classes the four permanent reasons as rejections', () => {
    const rejected = STATUS_REASONS.filter((reason) => describeReason(reason).outcome === 'REJECTED')

    expect(rejected.sort()).toEqual([
      'ENERGY_EXCEEDS_BATTERY_HEADROOM',
      'INSUFFICIENT_CONNECTOR_POWER',
      'NO_COMPATIBLE_CONNECTOR_TYPE',
      'WINDOW_EXPIRED',
    ])
  })

  it('tells a waitlisted driver that the next run retries the request', () => {
    expect(describeReason('LOST_CONTENTION').outcome).toBe('WAITLISTED')
    expect(describeReason('LOST_CONTENTION').sentence).toMatch(/next allocation run/)
  })

  it('names the grid limit for an insufficient-capacity reason', () => {
    expect(describeReason('INSUFFICIENT_GRID_CAPACITY').sentence).toMatch(/grid/)
  })

  it('falls back to the raw code for an unknown reason', () => {
    const text = describeReason('SOMETHING_NEW')

    expect(text.label).toBe('SOMETHING_NEW')
    expect(text.outcome).toBe('UNKNOWN')
  })
})
