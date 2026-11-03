import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { BenchmarkCase, BenchmarkMeasurement, BenchmarkRunResponse } from '../../types/api'
import { BenchmarksPage } from './BenchmarksPage'

function measurement(name: string, medianNs: number): BenchmarkMeasurement {
  return {
    name,
    trials: 7,
    medianNs,
    q1Ns: medianNs - 1_000,
    q3Ns: medianNs + 1_100,
    iqrNs: 2_100,
    minNs: medianNs - 2_000,
    maxNs: medianNs + 5_000,
    operationsMedian: 1_000,
    operationsMin: 1_000,
    operationsMax: 1_000,
    allocatedBytes: 2_592,
  }
}

const cases: BenchmarkCase[] = [
  {
    family: 'MATCHING',
    label: 'matching/small/density-8',
    size: 'SMALL',
    parameters: { requests: 100, connectors: 30, density: 8, edges: 800 },
    correct: true,
    algorithm: measurement('BIPARTITE_MATCHING', 96_100),
    baseline: measurement('GREEDY_FIRST_AVAILABLE', 21_000),
    algorithmQuality: 30,
    baselineQuality: 29,
    qualityUnit: 'matched requests',
    outcomes: { algorithmViolations: 0, baselineViolations: 0 },
  },
  {
    family: 'SELECTION',
    label: 'selection/small/uniform',
    size: 'SMALL',
    parameters: { n: 1000, k: 499, distribution: 'UNIFORM' },
    correct: true,
    algorithm: measurement('RANDOMIZED_QUICKSELECT', 14_900),
    baseline: measurement('FULL_SORT', 47_500),
    algorithmQuality: 524_689_312,
    baselineQuality: 524_689_312,
    qualityUnit: 'selected value',
    outcomes: {},
  },
  {
    family: 'RANGE_QUERY',
    label: 'range-query/small',
    size: 'SMALL',
    parameters: { n: 1000, operations: 1000, queries: 900, updates: 100 },
    correct: false,
    algorithm: null,
    baseline: null,
    algorithmQuality: 11,
    baselineQuality: 12,
    qualityUnit: 'answer checksum',
    outcomes: {},
  },
]

const configuration = {
  seed: 20261103,
  warmups: 3,
  trials: 7,
  sizes: ['SMALL' as const],
  families: ['MATCHING' as const, 'SELECTION' as const, 'RANGE_QUERY' as const],
}
const pending: BenchmarkRunResponse = { id: 9, seed: 20261103, configuration, status: 'PENDING', createdAt: '2026-11-03T01:00:00Z' }
const completed: BenchmarkRunResponse = {
  ...pending,
  status: 'COMPLETED',
  report: {
    ...configuration,
    environment: { javaVersion: '21.0.13', javaVendor: 'Eclipse Adoptium', vmName: 'OpenJDK 64-Bit Server VM', availableProcessors: 16, maxHeapBytes: 4_000_000_000, osName: 'Windows 11', osArch: 'amd64' },
    cases,
    totalNs: 72_000_000,
  },
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Routes by method and path; the run reports RUNNING once before it completes, so the page must poll. */
function stubApi(overrides: Record<string, () => Response> = {}) {
  let started = false
  let polls = 0
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url.replace(/^.*\/api\/v1/, '')}`
    if (overrides[key]) {
      return overrides[key]()
    }
    switch (key) {
      case 'GET /benchmarks':
        return json(started ? [pending] : [])
      case 'POST /benchmarks/run':
        started = true
        return json(pending, 202)
      case 'GET /benchmarks/9':
        polls += 1
        return json(polls === 1 ? { ...pending, status: 'RUNNING' } : completed)
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
      <BenchmarksPage />
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('BenchmarksPage', () => {
  it('queues a run, polls until it completes, and shows each family against its baseline', async () => {
    const fetchMock = stubApi()
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: /Medium/ }))
    await user.click(screen.getByRole('button', { name: 'Run benchmark' }))

    const matching = await screen.findByRole('table', { name: 'Bipartite matching against greedy first-available' }, { timeout: 4000 })
    const row = within(matching).getByRole('row', { name: /Small · density 8/ })
    const cells = within(row).getAllByRole('cell')
    expect(cells[0]).toHaveTextContent('96.1 µs')
    expect(cells[1]).toHaveTextContent('21.0 µs')
    expect(cells[2]).toHaveTextContent('Baseline 4.6× faster')
    expect(cells[3]).toHaveTextContent('30 vs 29 matched requests (algorithm +1)')

    const selection = screen.getByRole('table', { name: 'Randomized Quickselect against full sort' })
    expect(within(selection).getByRole('row', { name: /Small · uniform/ })).toHaveTextContent('Algorithm 3.2× faster')

    const range = screen.getByRole('table', { name: 'Segment tree against naive scan' })
    expect(within(range).getByRole('row', { name: /Small · 1,000 values/ })).toHaveTextContent('Verification failed, so this case was not timed')

    expect(screen.getByText(/Java 21\.0\.13/)).toBeInTheDocument()
    const started = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/benchmarks/run') && init?.method === 'POST')
    expect(JSON.parse(String(started?.[1]?.body))).toEqual({
      seed: 20261103,
      warmups: 3,
      trials: 7,
      sizes: ['SMALL', 'MEDIUM'],
      families: ['MATCHING', 'POWER', 'POWER_FEEDERS', 'SELECTION', 'RANGE_QUERY'],
    })
  })

  it('says so when a run failed', async () => {
    stubApi({
      'GET /benchmarks': () => json([{ ...pending, status: 'FAILED' }]),
      'GET /benchmarks/9': () => json({ ...pending, status: 'FAILED' }),
    })
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Seed 20261103/ }))

    expect(await screen.findByText(/This run failed before it finished/)).toBeInTheDocument()
  })

  it('shows a server validation error next to the form', async () => {
    stubApi({
      'POST /benchmarks/run': () => json({
        status: 400,
        code: 'VALIDATION_FAILED',
        message: 'Request validation failed',
        fieldErrors: [{ field: 'trials', message: 'must be less than or equal to 31' }],
      }, 400),
    })
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Run benchmark' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/trials: must be less than or equal to 31/)
  })
})
