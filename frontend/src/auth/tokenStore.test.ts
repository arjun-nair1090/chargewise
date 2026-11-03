import { afterEach, describe, expect, it } from 'vitest'
import type { TokenResponse } from '../types/api'
import { clearSession, loadSession, saveSession } from './tokenStore'

const token: TokenResponse = {
  accessToken: 'header.payload.signature',
  tokenType: 'Bearer',
  expiresAt: '2026-11-02T20:00:00Z',
  user: { id: 4, name: 'Asha Driver', email: 'asha@example.test', role: 'DRIVER', createdAt: '2026-11-01T09:00:00Z' },
}

afterEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

describe('tokenStore', () => {
  it('stores the token in sessionStorage only', () => {
    saveSession(token)

    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(1)
    const session = loadSession(Date.parse('2026-11-02T12:00:00Z'))
    expect(session?.accessToken).toBe('header.payload.signature')
    expect(session?.user.role).toBe('DRIVER')
  })

  it('drops an expired token', () => {
    saveSession(token)

    expect(loadSession(Date.parse('2026-11-02T20:00:00Z'))).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('ignores malformed stored data', () => {
    sessionStorage.setItem('chargewise.session', '{not json')

    expect(loadSession()).toBeNull()
  })

  it('clears the session', () => {
    saveSession(token)
    clearSession()

    expect(loadSession(Date.parse('2026-11-02T12:00:00Z'))).toBeNull()
  })
})
