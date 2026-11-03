import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../types/api'
import { homeFor, useAuth } from './authContext'

/** Route guard: anonymous visitors go to sign-in, signed-in users of the other role go to their own area. */
export function RequireRole({ role }: { role: Role }) {
  const { user } = useAuth()
  const location = useLocation()
  if (user === null) {
    return <Navigate to="/signin" replace state={{ from: location.pathname }} />
  }
  if (user.role !== role) {
    return <Navigate to={homeFor(user.role)} replace />
  }
  return <Outlet />
}
