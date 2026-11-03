import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../auth/AuthProvider'
import { RegisterPage, SignInPage } from './AuthPages'

const user = { id: 3, name: 'New Driver', email: 'new@example.test', role: 'DRIVER', createdAt: '2026-11-02T00:00:00Z' }
const token = { accessToken: 'a.b.c', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user }

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[{ pathname: path, state: { from: '/driver/bookings' } }]}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/signin" element={<SignInPage />} />
          <Route path="/driver" element={<p>Driver home</p>} />
          <Route path="/driver/garage" element={<p>Garage page</p>} />
          <Route path="/driver/bookings" element={<p>Bookings page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

afterEach(() => sessionStorage.clear())

describe('auth pages', () => {
  it('sends a newly registered driver to the garage', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(json(user, 201))
      .mockResolvedValueOnce(json(token, 200)))
    renderAt('/register')

    fireEvent.input(screen.getByLabelText('Name'), { target: { value: 'New Driver' } })
    fireEvent.input(screen.getByLabelText('Email'), { target: { value: 'new@example.test' } })
    fireEvent.input(screen.getByLabelText('Password'), { target: { value: 'long-enough-pass' } })
    await userEvent.setup().click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Garage page')).toBeInTheDocument()
  })

  it('returns a signed-in driver to the page they came from', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(token, 200)))
    renderAt('/signin')

    fireEvent.input(screen.getByLabelText('Email'), { target: { value: 'new@example.test' } })
    fireEvent.input(screen.getByLabelText('Password'), { target: { value: 'long-enough-pass' } })
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Bookings page')).toBeInTheDocument()
  })
})
