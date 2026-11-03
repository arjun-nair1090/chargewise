import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import type { Role } from '../types/api'
import { AuthProvider } from './AuthProvider'
import { RequireRole } from './RequireRole'
import { saveSession } from './tokenStore'

function signInAs(role: Role) {
  saveSession({
    accessToken: 'abc.def.ghi',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 1, name: 'User', email: 'user@example.test', role, createdAt: '2026-11-01T00:00:00Z' },
  })
}

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/signin" element={<p>Sign-in page</p>} />
          <Route element={<RequireRole role="DRIVER" />}>
            <Route path="/driver" element={<p>Driver home</p>} />
          </Route>
          <Route element={<RequireRole role="OPERATOR" />}>
            <Route path="/operator" element={<p>Operator home</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

afterEach(() => sessionStorage.clear())

describe('RequireRole', () => {
  it('redirects an anonymous visitor to sign-in', () => {
    renderAt('/operator')

    expect(screen.getByText('Sign-in page')).toBeInTheDocument()
  })

  it('redirects a driver away from the operator console', () => {
    signInAs('DRIVER')

    renderAt('/operator')

    expect(screen.getByText('Driver home')).toBeInTheDocument()
  })

  it('lets an operator into the operator console', () => {
    signInAs('OPERATOR')

    renderAt('/operator')

    expect(screen.getByText('Operator home')).toBeInTheDocument()
  })
})
