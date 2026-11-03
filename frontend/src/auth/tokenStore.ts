import type { TokenResponse, UserResponse } from '../types/api'

/** The signed-in session. It lives in sessionStorage only, so closing the tab signs the user out (Task 5.1). */
export type StoredSession = {
  accessToken: string
  expiresAt: string
  user: UserResponse
}

const KEY = 'chargewise.session'

export function saveSession(token: TokenResponse): StoredSession {
  const session: StoredSession = { accessToken: token.accessToken, expiresAt: token.expiresAt, user: token.user }
  sessionStorage.setItem(KEY, JSON.stringify(session))
  return session
}

export function loadSession(now: number = Date.now()): StoredSession | null {
  let session: StoredSession
  try {
    const raw = sessionStorage.getItem(KEY)
    if (raw === null) {
      return null
    }
    session = JSON.parse(raw) as StoredSession
  } catch {
    return null
  }
  const expires = Date.parse(session?.expiresAt)
  if (typeof session?.accessToken !== 'string' || Number.isNaN(expires) || expires <= now) {
    clearSession()
    return null
  }
  return session
}

export function clearSession() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // Storage can be unavailable (private mode); there is then nothing to clear.
  }
}
