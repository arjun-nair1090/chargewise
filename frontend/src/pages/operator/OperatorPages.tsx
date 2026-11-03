import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { QueryState } from '../../components/QueryState'
import { StationMap } from '../../components/StationMap'
import { DataBadge, StatusChip } from '../../components/StatusChip'
import { buttonClass } from '../../components/buttonClass'
import { Button, EmptyState, Mono, PageHeader, Panel, Readout, ReadoutStrip } from '../../components/ui'
import { outcomeSentence } from '../../lib/bookingOutcome'
import { connectorLabel, formatDateTime, formatDuration, formatKw, formatKwh, formatNumber, formatRatio, formatWindow, humanise } from '../../lib/format'
import { operatorApi } from '../../services/endpoints'
import {
  BOOKING_STATUSES,
  SESSION_STATUSES,
  type BookingStatus,
  type DashboardResponse,
  type Page,
  type SessionStatus,
} from '../../types/api'
import { useStationNames, useStations } from './useStationNames'

function CountLedger({ title, counts }: { title: string; counts: Record<string, number> }) {
  const total = Object.values(counts).reduce((sum, count) => sum + count, 0)
  return (
    <Panel title={title} aside={`${total} total`} bodyClassName="px-5 py-2">
      <dl className="flex flex-col">
        {Object.entries(counts).map(([status, count]) => (
          <div key={status} className="flex items-center justify-between border-b border-border py-2 text-sm last:border-b-0">
            <dt><StatusChip status={status} /></dt>
            <dd className="tabular-nums">{count}</dd>
          </div>
        ))}
      </dl>
    </Panel>
  )
}

function Kpis({ data }: { data: DashboardResponse }) {
  const { utilisation, waiting } = data
  const available = Math.max(0, utilisation.operationalConnectors - utilisation.activeSessions)
  return (
    <ReadoutStrip label="Network now">
      <Readout
        hero
        label="Allocated power"
        value={formatNumber(utilisation.activePowerKw)}
        unit="kW"
        note={`${formatRatio(utilisation.powerUtilisation)} of ${formatKw(utilisation.operationalGridKw)} operational grid`}
      />
      <Readout
        label="Available connectors"
        value={available}
        note={`${utilisation.activeSessions} charging of ${utilisation.operationalConnectors} operational`}
      />
      <Readout
        label="Median wait to assignment"
        value={formatDuration(waiting.p50AssignWaitSeconds)}
        note={`p90 ${formatDuration(waiting.p90AssignWaitSeconds)} · ${waiting.assignedSampleSize} assigned`}
      />
      <Readout
        label="Connector utilisation"
        value={formatRatio(utilisation.connectorUtilisation)}
        note={`${waiting.queueSize} requests queued`}
      />
    </ReadoutStrip>
  )
}

export function OverviewPage() {
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: operatorApi.dashboard, refetchInterval: 30_000 })
  const stations = useStations()
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Network overview"
        job="Where power and connectors stand right now, and how long requests wait for an assignment."
        badges={<><DataBadge kind="synthetic" /><DataBadge kind="simulated" /></>}
        actions={<Link to="/operator/allocation" className={buttonClass('primary')}>Go to allocation</Link>}
      />
      <QueryState query={dashboard} rows={3}>
        {(data) => (
          <div className="flex flex-col gap-6">
            <Kpis data={data} />
            <p className="text-sm text-muted">
              Waiting percentiles are nearest-rank values chosen by {humanise(data.waiting.algorithm)}. Updated{' '}
              {formatDateTime(data.generatedAt)}.
              {data.latestAllocationRunId !== null && (
                <> Latest run: <Link to={`/operator/allocation/${data.latestAllocationRunId}`} className="text-text underline">#{data.latestAllocationRunId}</Link>.</>
              )}
            </p>
            <div className="grid gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
              <div className="flex min-w-0 flex-col gap-3">
                {stations.data && <StationMap stations={stations.data.content} height="26rem" />}
                <p className="text-xs text-muted">Lime operational, amber maintenance, coral offline. Station status counts are listed beside the map.</p>
              </div>
              <div className="flex flex-col gap-6">
                <CountLedger title="Requests" counts={data.bookings} />
                <CountLedger title="Stations" counts={data.stations} />
              </div>
            </div>
            <div className="grid gap-6 md:grid-cols-2">
              <CountLedger title="Sessions" counts={data.sessions} />
              <CountLedger title="Connectors" counts={data.connectors} />
            </div>
          </div>
        )}
      </QueryState>
    </div>
  )
}

function Pager<T>({ page, onPage }: { page: Page<T>; onPage: (page: number) => void }) {
  if (page.totalPages <= 1) {
    return null
  }
  return (
    <div className="flex items-center justify-between gap-3 border-t border-border px-5 py-3 text-sm text-muted">
      <span>Page {page.page + 1} of {page.totalPages}</span>
      <div className="flex gap-2">
        <Button size="sm" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>Previous</Button>
        <Button size="sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>Next</Button>
      </div>
    </div>
  )
}

function StatusFilter<S extends string>({ statuses, value, onChange }: { statuses: readonly S[]; value: S | ''; onChange: (value: S | '') => void }) {
  return (
    <label className="inline-flex items-center gap-2 text-sm">
      <span className="text-muted">Status</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value as S | '')}
        className="h-8 rounded-md border border-border bg-bg px-2 text-sm text-text"
      >
        <option value="">All</option>
        {statuses.map((status) => <option key={status} value={status}>{humanise(status)}</option>)}
      </select>
    </label>
  )
}

export function RequestsPage() {
  const [status, setStatus] = useState<BookingStatus | ''>('')
  const [page, setPage] = useState(0)
  const requests = useQuery({
    queryKey: ['operator', 'requests', status, page],
    queryFn: () => operatorApi.requests(status || undefined, page),
  })
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Request queue"
        job="Every driver's requests, newest first, with the reason the last allocation run gave each one."
        badges={<DataBadge kind="synthetic" />}
      />
      <Panel
        title="Requests"
        aside={<StatusFilter statuses={BOOKING_STATUSES} value={status} onChange={(value) => { setStatus(value); setPage(0) }} />}
        bodyClassName=""
      >
        <QueryState
          query={requests}
          rows={8}
          isEmpty={(data) => data.content.length === 0}
          empty={<EmptyState title="No requests with this status" />}
        >
          {(data) => (
            <>
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Request</th>
                      <th scope="col">Vehicle</th>
                      <th scope="col">Station</th>
                      <th scope="col">Window</th>
                      <th scope="col" className="text-right">Energy</th>
                      <th scope="col">Status</th>
                      <th scope="col">Why</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((booking) => (
                      <tr key={booking.id}>
                        <td><Mono>#{booking.id}</Mono></td>
                        <td><Mono>{booking.vehicleRegistration}</Mono></td>
                        <td className="whitespace-nowrap">{booking.stationName}</td>
                        <td className="whitespace-nowrap">{formatWindow(booking.requestedStart, booking.requestedEnd)}</td>
                        <td className="text-right">{formatKwh(booking.requestedEnergyKwh)}</td>
                        <td><StatusChip status={booking.status} /></td>
                        <td className="min-w-64 text-muted">{outcomeSentence(booking)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={data} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Panel>
    </div>
  )
}

export function SessionsPage() {
  const [status, setStatus] = useState<SessionStatus | ''>('ACTIVE')
  const [page, setPage] = useState(0)
  const stationNames = useStationNames()
  const sessions = useQuery({
    queryKey: ['operator', 'sessions', status, page],
    queryFn: () => operatorApi.sessions(status || undefined, page),
    refetchInterval: 30_000,
  })
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Sessions"
        job="Charging sessions across the network with their allocated power and simulated delivery."
        badges={<DataBadge kind="simulated" />}
      />
      <Panel
        title="Sessions"
        aside={<StatusFilter statuses={SESSION_STATUSES} value={status} onChange={(value) => { setStatus(value); setPage(0) }} />}
        bodyClassName=""
      >
        <QueryState
          query={sessions}
          rows={8}
          isEmpty={(data) => data.content.length === 0}
          empty={
            <EmptyState title="No sessions with this status">
              Sessions become active when their window starts. Choose another status, or run allocation first.
            </EmptyState>
          }
        >
          {(data) => (
            <>
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Session</th>
                      <th scope="col">Vehicle</th>
                      <th scope="col">Station</th>
                      <th scope="col">Connector</th>
                      <th scope="col">Window</th>
                      <th scope="col" className="text-right">Power</th>
                      <th scope="col" className="text-right">Delivered</th>
                      <th scope="col">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((session) => (
                      <tr key={session.id}>
                        <td><Mono>#{session.id}</Mono></td>
                        <td><Mono>{session.vehicleRegistration}</Mono></td>
                        <td className="whitespace-nowrap">{stationNames.get(session.stationId) ?? session.stationName}</td>
                        <td>{connectorLabel(session.connectorType)} <Mono className="text-muted">#{session.connectorId}</Mono></td>
                        <td className="whitespace-nowrap">{formatWindow(session.windowStart, session.windowEnd)}</td>
                        <td className="text-right">{formatKw(session.allocatedPowerKw)}</td>
                        <td className="text-right whitespace-nowrap">{formatKwh(session.energyDeliveredKwh)} of {formatKwh(session.requestedEnergyKwh)}</td>
                        <td><StatusChip status={session.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={data} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Panel>
    </div>
  )
}
