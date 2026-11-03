import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AllocationRunResponse } from '../../types/api'
import { AllocationOutcome } from './AllocationOutcome'

const OBJECTIVE =
  'Matching maximises the number of request–connector assignments in the modeled compatibility graph. '
  + 'Edmonds-Karp maximises the total feasible power in the modeled network. '
  + 'Neither minimises waiting time or guarantees fairness.'

const run: AllocationRunResponse = {
  id: 9,
  intervalStart: '2026-11-02T10:00:00Z',
  intervalEnd: '2026-11-17T10:00:00Z',
  createdAt: '2026-11-02T10:01:00Z',
  result: {
    matchingAlgorithm: 'BIPARTITE_MATCHING',
    powerAlgorithm: 'EDMONDS_KARP',
    selectionAlgorithm: 'RANDOMIZED_QUICKSELECT',
    eligibleRequests: 5,
    assignedCount: 2,
    waitlistedCount: 2,
    rejectedCount: 1,
    assignments: [
      { bookingId: 101, stationId: 1, connectorId: 11, sessionId: 501, requestedKw: 22, allocatedKw: 22 },
      { bookingId: 102, stationId: 2, connectorId: 21, sessionId: 502, requestedKw: 60, allocatedKw: 40 },
    ],
    unassigned: [
      { bookingId: 103, stationId: 1, status: 'WAITLISTED', reason: 'LOST_CONTENTION' },
      { bookingId: 104, stationId: 2, status: 'WAITLISTED', reason: 'LOST_CONTENTION' },
      { bookingId: 105, stationId: 2, status: 'REJECTED', reason: 'NO_COMPATIBLE_CONNECTOR_TYPE' },
    ],
    stations: [
      { stationId: 1, gridKw: 100, demandKw: 22, demandCapacityRatio: 0.22, committedPeakKw: 0, availableKw: 100, allocatedKw: 22 },
      { stationId: 2, gridKw: 40, demandKw: 60, demandCapacityRatio: 1.5, committedPeakKw: 0, availableKw: 40, allocatedKw: 40 },
    ],
    bottlenecks: [
      { kind: 'SESSION_DEMAND', stationId: null, bookingId: 101, capacityKw: 22 },
      { kind: 'STATION_GRID', stationId: 2, bookingId: null, capacityKw: 40 },
    ],
    requestedPower: { p50Kw: 22, p75Kw: 60, p90Kw: 60, seed: 7, comparisons: 9, swaps: 3 },
    metrics: {
      compatibilityEdges: 6,
      matchingCardinality: 2,
      edgesExamined: 8,
      augmentations: 2,
      flowOperations: 30,
      totalRequestedKw: 82,
      totalAllocatedKw: 62,
      unservedKw: 20,
      selectionNs: 1000,
      matchingNs: 2000,
      powerNs: 3000,
      totalNs: 9000,
    },
  },
}

const names = new Map([[1, 'Andheri Hub'], [2, 'Bandra Depot']])

function renderOutcome(override: Partial<AllocationRunResponse['result']> = {}) {
  return render(
    <MemoryRouter>
      <AllocationOutcome run={{ ...run, result: { ...run.result, ...override } }} stationNames={names} />
    </MemoryRouter>,
  )
}

afterEach(() => vi.restoreAllMocks())

describe('AllocationOutcome', () => {
  it('shows the objective statement verbatim', () => {
    renderOutcome()

    const statement = screen.getByRole('note', { name: 'Objective' })
    expect(statement.textContent?.replace(/\s+/g, ' ').trim()).toContain(OBJECTIVE)
  })

  it('groups unassigned requests by reason and filters the ledger by one', async () => {
    renderOutcome()
    const user = userEvent.setup()

    const lostContention = screen.getByRole('button', { name: /Lost contention/ })
    expect(lostContention).toHaveTextContent('2')
    expect(screen.getByRole('button', { name: /No compatible connector/ })).toHaveTextContent('1')

    await user.click(lostContention)

    const ledger = screen.getByRole('table', { name: 'Unassigned requests' })
    expect(within(ledger).getByText('#103')).toBeInTheDocument()
    expect(within(ledger).queryByText('#105')).not.toBeInTheDocument()
    expect(lostContention).toHaveAttribute('aria-pressed', 'true')
  })

  it('marks the station whose grid limit binds', () => {
    renderOutcome()

    const bandra = screen.getByRole('listitem', { name: 'Bandra Depot' })
    const andheri = screen.getByRole('listitem', { name: 'Andheri Hub' })
    expect(within(bandra).getByText('Grid limit binds')).toBeInTheDocument()
    expect(within(andheri).queryByText('Grid limit binds')).not.toBeInTheDocument()
  })

  it('lists stations whose grid limit binds first', () => {
    renderOutcome()

    const rows = within(screen.getByRole('list', { name: 'Station power' })).getAllByRole('listitem')
    expect(rows.map((row) => row.getAttribute('aria-label'))).toEqual(['Bandra Depot', 'Andheri Hub'])
  })

  it('says so when no station grid limit binds', () => {
    renderOutcome({ bottlenecks: [{ kind: 'SESSION_DEMAND', stationId: null, bookingId: 101, capacityKw: 22 }] })

    expect(screen.getByText("No station's grid limit binds this run")).toBeInTheDocument()
  })

  it('gives the main reason at a station where no request was matched', () => {
    names.set(3, 'Goregaon Yard')
    renderOutcome({
      stations: [
        ...run.result.stations,
        { stationId: 3, gridKw: 130, demandKw: 136.8, demandCapacityRatio: 1.05, committedPeakKw: null, availableKw: null, allocatedKw: null },
      ],
      unassigned: [
        ...run.result.unassigned,
        { bookingId: 106, stationId: 3, status: 'WAITLISTED', reason: 'STATION_NOT_OPERATIONAL' },
        { bookingId: 107, stationId: 3, status: 'WAITLISTED', reason: 'STATION_NOT_OPERATIONAL' },
      ],
    })

    const goregaon = screen.getByRole('listitem', { name: 'Goregaon Yard' })
    expect(within(goregaon).getByText(/No request matched: station not operational \(2\)/)).toBeInTheDocument()
  })

  it('brings the filtered ledger into view when a reason is selected', async () => {
    const scrollIntoView = vi.spyOn(Element.prototype, 'scrollIntoView')
    renderOutcome()

    await userEvent.setup().click(screen.getByRole('button', { name: /Lost contention/ }))

    expect(scrollIntoView).toHaveBeenCalledTimes(1)
    expect(scrollIntoView.mock.contexts[0]).toBe(screen.getByRole('region', { name: 'Unassigned requests' }))
  })
})
