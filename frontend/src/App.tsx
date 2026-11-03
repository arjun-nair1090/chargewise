import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentType } from 'react'
import { Navigate, RouterProvider, createBrowserRouter } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireRole } from './auth/RequireRole'
import { homeFor, useAuth } from './auth/authContext'
import { ApiError } from './services/api'
import { AppShell } from './layouts/AppShell'
import { RegisterPage, SignInPage } from './pages/auth/AuthPages'

/** Route-level code splitting: map and chart libraries load with the pages that use them. */
function page<M extends Record<string, unknown>>(load: () => Promise<M>, name: keyof M & string) {
  return async () => ({ Component: (await load())[name] as ComponentType })
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      refetchOnWindowFocus: false,
      // Client errors (validation, 401/403/404) will not change on retry; network and server errors may.
      retry: (failureCount, error) => failureCount < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
    },
  },
})

function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homeFor(user.role) : '/signin'} replace />
}

const router = createBrowserRouter([
  { path: '/signin', element: <SignInPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <RequireRole role="DRIVER" />,
    children: [
      {
        path: 'driver',
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="stations" replace /> },
          { path: 'stations', lazy: page(() => import('./pages/driver/StationsPage'), 'StationsPage') },
          { path: 'stations/:stationId', lazy: page(() => import('./pages/driver/StationDetailPage'), 'StationDetailPage') },
          { path: 'bookings', lazy: page(() => import('./pages/driver/BookingPages'), 'BookingsPage') },
          { path: 'bookings/:bookingId', lazy: page(() => import('./pages/driver/BookingPages'), 'BookingDetailPage') },
          { path: 'sessions/:sessionId', lazy: page(() => import('./pages/driver/BookingPages'), 'SessionPage') },
          { path: 'history', lazy: page(() => import('./pages/driver/BookingPages'), 'HistoryPage') },
          { path: 'garage', lazy: page(() => import('./pages/driver/GaragePage'), 'GaragePage') },
        ],
      },
    ],
  },
  {
    element: <RequireRole role="OPERATOR" />,
    children: [
      {
        path: 'operator',
        element: <AppShell />,
        children: [
          { index: true, lazy: page(() => import('./pages/operator/OperatorPages'), 'OverviewPage') },
          { path: 'allocation', lazy: page(() => import('./pages/operator/AllocationPage'), 'AllocationPage') },
          { path: 'allocation/:runId', lazy: page(() => import('./pages/operator/AllocationPage'), 'AllocationPage') },
          { path: 'requests', lazy: page(() => import('./pages/operator/OperatorPages'), 'RequestsPage') },
          { path: 'sessions', lazy: page(() => import('./pages/operator/OperatorPages'), 'SessionsPage') },
          { path: 'stations', lazy: page(() => import('./pages/operator/StationsAdminPage'), 'StationsAdminPage') },
          { path: 'analytics', lazy: page(() => import('./pages/analytics/AnalyticsPage'), 'AnalyticsPage') },
          { path: 'lab', lazy: page(() => import('./pages/analytics/AlgorithmLabPage'), 'AlgorithmLabPage') },
          { path: 'simulations', lazy: page(() => import('./pages/analytics/SimulationsPage'), 'SimulationsPage') },
          { path: 'benchmarks', lazy: page(() => import('./pages/analytics/BenchmarksPage'), 'BenchmarksPage') },
        ],
      },
    ],
  },
  { path: '*', element: <HomeRedirect /> },
])

export default function App() {
  return (
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </AuthProvider>
  )
}
