import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AlgorithmLabPage } from './AlgorithmLabPage'

function renderLab() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AlgorithmLabPage />
    </QueryClientProvider>,
  )
}

describe('AlgorithmLabPage', () => {
  it('rejects malformed JSON before sending anything', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    renderLab()
    const user = userEvent.setup()

    fireEvent.change(screen.getByLabelText('Input JSON'), { target: { value: '{"requests": [' } })
    await user.click(screen.getByRole('button', { name: 'Execute' }))

    expect(await screen.findByText(/not valid JSON/)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows server validation errors inline', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Request validation failed',
      fieldErrors: [{ field: 'edges[0].connector', message: 'unknown connector C9' }],
    }), { status: 400, headers: { 'Content-Type': 'application/json' } })))
    renderLab()
    const user = userEvent.setup()

    fireEvent.change(screen.getByLabelText('Input JSON'), {
      target: { value: '{"requests":["R1"],"connectors":["C1"],"edges":[{"request":"R1","connector":"C9"}]}' },
    })
    await user.click(screen.getByRole('button', { name: 'Execute' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Request validation failed')
    expect(alert).toHaveTextContent('edges[0].connector')
    expect(alert).toHaveTextContent('unknown connector C9')
  })
})
