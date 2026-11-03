import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../services/api'
import type { HealthResponse } from '../types/api'

const HEALTH_REFRESH_MS = 30_000

function StatusDot({ tone }: { tone: 'ok' | 'warn' | 'error' | 'idle' }) {
  const colour = {
    ok: 'bg-accent',
    warn: 'bg-warning',
    error: 'bg-danger',
    idle: 'bg-muted animate-pulse',
  }[tone]
  return <span aria-hidden="true" className={`inline-block size-2 rounded-full ${colour}`} />
}

export function BackendStatus() {
  const health = useQuery({
    queryKey: ['health'],
    queryFn: () => apiFetch<HealthResponse>('/health'),
    refetchInterval: HEALTH_REFRESH_MS,
  })

  const base = 'inline-flex items-center gap-2 text-sm'

  if (health.isPending) {
    return (
      <div role="status" aria-busy="true" className={`${base} text-muted`}>
        <StatusDot tone="idle" />
        Checking backend…
      </div>
    )
  }

  if (health.isError) {
    return (
      <div role="alert" className={`${base} text-danger`}>
        <StatusDot tone="error" />
        Backend unreachable
      </div>
    )
  }

  const databaseUp = health.data.database === 'UP'
  return (
    <div role="status" aria-busy="false" className={`${base} text-muted`}>
      <StatusDot tone={databaseUp ? 'ok' : 'warn'} />
      {databaseUp ? 'Backend online' : 'Database unavailable'}
    </div>
  )
}
