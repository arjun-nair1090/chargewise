import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadSession, saveSession } from '../auth/tokenStore'
import { ApiError, apiFetch, setUnauthorizedHandler } from './api'

function stubFetch(status: number, body: unknown) {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function signIn() {
  saveSession({
    accessToken: 'abc.def.ghi',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 1, name: 'Op', email: 'op@example.test', role: 'OPERATOR', createdAt: '2026-11-01T00:00:00Z' },
  })
}

afterEach(() => {
  sessionStorage.clear()
  setUnauthorizedHandler(null)
})

describe('apiFetch', () => {
  it('sends the bearer token when signed in', async () => {
    signIn()
    const fetchMock = stubFetch(200, { ok: true })

    await apiFetch('/auth/me')

    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1]
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer abc.def.ghi')
  })

  it('sends no Authorization header when signed out', async () => {
    const fetchMock = stubFetch(200, { ok: true })

    await apiFetch('/health')

    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1]
    expect(new Headers(init.headers).has('Authorization')).toBe(false)
  })

  it('clears the token and notifies on a 401 for an authenticated call', async () => {
    signIn()
    stubFetch(401, { status: 401, code: 'UNAUTHORIZED', message: 'Authentication required' })
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)

    await expect(apiFetch('/vehicles')).rejects.toBeInstanceOf(ApiError)

    expect(loadSession()).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('does not treat a failed sign-in as an expired session', async () => {
    stubFetch(401, { status: 401, code: 'UNAUTHORIZED', message: 'Invalid email or password' })
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)

    await expect(apiFetch('/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({ status: 401 })

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('sends JSON bodies with a JSON content type', async () => {
    const fetchMock = stubFetch(201, { id: 1 })

    await apiFetch('/auth/register', { method: 'POST', json: { name: 'A' } })

    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1]
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
    expect(init.body).toBe('{"name":"A"}')
  })
})
