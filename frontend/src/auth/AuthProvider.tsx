import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '../services/api'
import type { TokenResponse } from '../types/api'
import { AuthContext } from './authContext'
import { clearSession, loadSession, saveSession, type StoredSession } from './tokenStore'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<StoredSession | null>(() => loadSession())
  const [expired, setExpired] = useState(false)

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setSession(null)
      setExpired(true)
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  const signIn = useCallback((token: TokenResponse) => {
    setSession(saveSession(token))
    setExpired(false)
  }, [])

  const signOut = useCallback(() => {
    clearSession()
    setSession(null)
    setExpired(false)
  }, [])

  const value = useMemo(
    () => ({ user: session?.user ?? null, expired, signIn, signOut }),
    [session, expired, signIn, signOut],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
