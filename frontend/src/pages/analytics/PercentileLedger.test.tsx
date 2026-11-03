import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PercentileLedger } from './PercentileLedger'

const waiting = {
  algorithm: 'RANDOMIZED_QUICKSELECT',
  assignedSampleSize: 37,
  p50AssignWaitSeconds: 8520,
  p90AssignWaitSeconds: 9000,
  queueSize: 52,
  p50QueueAgeSeconds: 17820,
  p90QueueAgeSeconds: 18000,
}
const power = { p50Kw: 6.66, p75Kw: 12.66, p90Kw: 18.28, seed: 1793637900, comparisons: 745, swaps: 719 }

describe('PercentileLedger', () => {
  it('puts every percentile in one table, a row per measure', () => {
    render(<PercentileLedger waiting={waiting} power={power} runId={2} />)

    const table = screen.getByRole('table', { name: 'Percentiles' })
    const wait = within(table).getByRole('row', { name: /Wait to assignment/ })
    expect(within(wait).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['2 h 22 min', '—', '2 h 30 min', '37 assigned'])
    const requested = within(table).getByRole('row', { name: /Requested power/ })
    expect(within(requested).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['6.66 kW', '12.66 kW', '18.28 kW', 'Run #2'])
    expect(screen.getByText(/745 comparisons, 719 swaps, seed 1793637900/)).toBeInTheDocument()
    expect(screen.getByText(/Randomized Quickselect/)).toBeInTheDocument()
  })

  it('says when there is no allocation run to take power percentiles from', () => {
    render(<PercentileLedger waiting={waiting} power={undefined} runId={null} />)

    const requested = screen.getByRole('row', { name: /Requested power/ })
    expect(within(requested).getAllByRole('cell').at(-1)?.textContent).toBe('No run yet')
  })
})
