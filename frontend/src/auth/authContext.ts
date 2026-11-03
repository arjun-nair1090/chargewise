import { createContext, useContext } from 'react'
import type { Role, TokenResponse, UserResponse } from '../types/api'

export type AuthState = {
  user: UserResponse | null
  /** True when the last session ended because the server rejected the token. */
  expired: boolean
  signIn: (token: TokenResponse) => void
  signOut: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}

export function homeFor(role: Role): string {
  return role === 'OPERATOR' ? '/operator' : '/driver'
}
