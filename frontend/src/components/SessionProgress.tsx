import { connectorLabel, formatKw, formatNumber, formatWindow } from '../lib/format'
import type { SessionResponse } from '../types/api'
import { DataBadge, StatusChip } from './StatusChip'

/** Delivered against requested energy, exactly as the backend's lifecycle job reports it. */
export function SessionProgress({ session }: { session: SessionResponse }) {
  const ratio = session.requestedEnergyKwh > 0 ? session.energyDeliveredKwh / session.requestedEnergyKwh : 0
  const percent = Math.min(100, Math.round(ratio * 100))
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <StatusChip status={session.status} />
        <DataBadge kind="simulated" />
      </div>
      <p className="text-3xl font-semibold tabular-nums tracking-tight">
        {formatNumber(session.energyDeliveredKwh)} of {formatNumber(session.requestedEnergyKwh)} kWh
      </p>
      <div
        role="progressbar"
        aria-label="Energy delivered"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="h-2 w-full overflow-hidden rounded-sm bg-surface-raised"
      >
        <div className="h-full bg-accent transition-[width] duration-200 ease-out" style={{ width: `${percent}%` }} />
      </div>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
        <div><dt className="text-muted">Window</dt><dd>{formatWindow(session.windowStart, session.windowEnd)}</dd></div>
        <div><dt className="text-muted">Allocated power</dt><dd className="tabular-nums">{formatKw(session.allocatedPowerKw)}</dd></div>
        <div><dt className="text-muted">Station</dt><dd>{session.stationName}</dd></div>
        <div><dt className="text-muted">Connector</dt><dd>{connectorLabel(session.connectorType)} #{session.connectorId}</dd></div>
      </dl>
      <p className="text-xs text-muted">
        Progress comes from a constant-power model: delivered energy is allocated power × elapsed time, capped at the
        requested energy. No real charger is involved.
      </p>
    </div>
  )
}
