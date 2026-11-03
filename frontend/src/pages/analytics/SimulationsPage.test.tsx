import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SimulationComparisonResponse, SimulationMetrics, SimulationResponse } from '../../types/api'
import { SimulationsPage } from './SimulationsPage'

const simulation: SimulationResponse = {
  id: 4,
  name: 'High contention',
  seed: 7,
  configuration: { scenario: 'HIGH_CONTENTION', stations: 3, connectorsPerStation: 2, requests: 300, horizonSlots: 96, maxWaitSlots: 8 },
  status: 'COMPLETED',
  createdAt: '2026-11-03T01:00:00Z',
}

function metrics(strategy: 'ALGORITHM' | 'BASELINE', matched: number): SimulationMetrics {
  return {
    strategy,
    matchingAlgorithm: strategy === 'ALGORITHM' ? 'BIPARTITE_MATCHING' : 'GREEDY_FIRST_AVAILABLE',
    powerAlgorithm: strategy === 'ALGORITHM' ? 'EDMONDS_KARP' : 'GREEDY_PRIORITY',
    requestCount: 300,
    matchedCount: matched,
    unmatchedCount: 300 - matched,
    expiredCount: 300 - matched,
    pendingAtHorizonEnd: 0,
    completedSessions: matched,
    interruptedSessions: 0,
    requestedEnergyKwh: 6750,
    deliveredEnergyKwh: 2000,
    totalRequestedKw: 30000,
    totalAllocatedKw: 8000,
    unservedKw: 22000,
    stationUtilisation: 0.81,
    stationUtilisationByStation: [0.8, 0.82, 0.81],
    waitSampleSize: matched,
    waitP50Minutes: 45,
    waitP90Minutes: 105,
    capacityViolations: 0,
    matchingViolations: 0,
    slotsWhereBaselineMatchedMore: 0,
    slotsWhereBaselineAllocatedMore: 0,
    matchedGainOverBaseline: strategy === 'ALGORITHM' ? 6 : 0,
    allocatedGainOverBaselineKw: 0,
    edgesExamined: 900,
    powerOperations: 4000,
    matchingNs: 1_000_000,
    powerNs: 2_000_000,
    totalNs: 9_000_000,
  }
}

const comparison: SimulationComparisonResponse = {
  simulationId: 4,
  name: 'High contention',
  scenario: 'HIGH_CONTENTION',
  seed: 7,
  inputHash: 'ab12'.repeat(16),
  algorithm: metrics('ALGORITHM', 85),
  baseline: metrics('BASELINE', 86),
  rows: [
    { metric: 'matchedCount', label: 'Requests started', unit: 'requests', algorithm: 85, baseline: 86, difference: -1, betterWhen: 'HIGHER' },
    { metric: 'waitP50Minutes', label: 'Median wait to start', unit: 'min', algorithm: 45, baseline: 45, difference: 0, betterWhen: 'LOWER' },
  ],
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Routes by method and path, so the order React Query fetches in does not matter. */
function stubApi(overrides: Record<string, () => Response> = {}) {
  let created = false
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url.replace(/^.*\/api\/v1/, '')}`
    if (overrides[key]) {
      return overrides[key]()
    }
    switch (key) {
      case 'GET /simulations':
        return json(created ? [simulation] : [])
      case 'POST /simulations':
        created = true
        return json({ ...simulation, status: 'PENDING' }, 201)
      case 'POST /simulations/4/run':
        return json({ simulation, inputHash: comparison.inputHash })
      case 'GET /simulations/4/comparison':
        return json(comparison)
      default:
        return json({ status: 404, code: 'NOT_FOUND', message: `${key} not stubbed`, fieldErrors: [] }, 404)
    }
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SimulationsPage />
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('SimulationsPage', () => {
  it('runs a scenario and shows the server comparison with the per-slot evidence', async () => {
    const fetchMock = stubApi()
    renderPage()
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Scenario'), 'HIGH_CONTENTION')
    await user.click(screen.getByRole('button', { name: 'Run simulation' }))

    const ledger = await screen.findByRole('table', { name: 'Algorithm against baseline' })
    const started = within(ledger).getByRole('row', { name: /Requests started/ })
    expect(within(started).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['85', '86', '−1 (baseline ahead)'])
    expect(screen.getByText(/In no slot did greedy matching start more requests/)).toBeInTheDocument()
    expect(screen.getByText(comparison.inputHash.slice(0, 12), { exact: false })).toBeInTheDocument()
    const created = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/simulations') && init?.method === 'POST')
    expect(JSON.parse(String(created?.[1]?.body))).toMatchObject({ scenario: 'HIGH_CONTENTION', seed: 20261102 })
  })

  it('re-runs a stored simulation and reports that it reproduced', async () => {
    stubApi({
      'GET /simulations': () => json([simulation]),
      'POST /simulations/4/run': () => json({ simulation, inputHash: comparison.inputHash, reproducedPreviousRun: true }),
    })
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /High contention/ }))
    await user.click(await screen.findByRole('button', { name: 'Re-run and verify' }))

    expect(await screen.findByText(/Reproduced: identical input hash and identical results/)).toBeInTheDocument()
  })

  it('shows a server validation error next to the form', async () => {
    stubApi({
      'POST /simulations': () => json({
        status: 400,
        code: 'VALIDATION_FAILED',
        message: 'Request validation failed',
        fieldErrors: [{ field: 'requests', message: 'must be less than or equal to 5000' }],
      }, 400),
    })
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Run simulation' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/requests: must be less than or equal to 5000/)
  })
})
