import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { BackendStatus } from './BackendStatus'

function renderWithQueryClient() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <BackendStatus />
    </QueryClientProvider>,
  )
}

function stubFetchResponse(body: unknown, status = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })),
  )
}

describe('BackendStatus', () => {
  it('shows the backend as online when health and database are UP', async () => {
    stubFetchResponse({ status: 'UP', database: 'UP', version: '0.1.0' })

    renderWithQueryClient()

    expect(await screen.findByText('Backend online')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/v1/health', expect.anything())
  })

  it('warns when the backend is up but the database is down', async () => {
    stubFetchResponse({ status: 'UP', database: 'DOWN', version: '0.1.0' })

    renderWithQueryClient()

    expect(await screen.findByText('Database unavailable')).toBeInTheDocument()
  })

  it('shows an alert when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    }))

    renderWithQueryClient()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Backend unreachable')
  })

  it('marks itself busy while the health check is pending', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})))

    renderWithQueryClient()

    expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
  })
})
