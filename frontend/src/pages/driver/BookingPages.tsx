import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { QueryState } from '../../components/QueryState'
import { SessionProgress } from '../../components/SessionProgress'
import { DataBadge, StatusChip } from '../../components/StatusChip'
import { buttonClass } from '../../components/buttonClass'
import { Button, EmptyState, FormError, Mono, PageHeader, Panel } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { connectorLabel, formatDateTime, formatKw, formatKwh, formatWindow } from '../../lib/format'
import { outcomeSentence } from '../../lib/bookingOutcome'
import { describeReason } from '../../lib/reasons'
import { bookingApi, sessionApi } from '../../services/endpoints'
import type { BookingResponse } from '../../types/api'

const CANCELLABLE = new Set(['PENDING', 'WAITLISTED', 'ASSIGNED'])

function CancelButton({ booking }: { booking: BookingResponse }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const [openedAt] = useState(() => Date.now())
  const cancel = useMutation({
    mutationFn: () => bookingApi.cancel(booking.id),
    onSuccess: async () => {
      setConfirming(false)
      await queryClient.invalidateQueries({ queryKey: ['bookings'] })
      await queryClient.invalidateQueries({ queryKey: ['booking', booking.id] })
    },
  })
  if (!CANCELLABLE.has(booking.status) || Date.parse(booking.requestedStart) <= openedAt) {
    return null
  }
  return (
    <div className="flex flex-col items-start gap-2">
      {confirming ? (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="danger" busy={cancel.isPending} onClick={() => cancel.mutate()}>Confirm cancellation</Button>
          <Button size="sm" variant="ghost" onClick={() => setConfirming(false)}>Keep request</Button>
        </div>
      ) : (
        <Button size="sm" onClick={() => setConfirming(true)}>Cancel request</Button>
      )}
      {cancel.isError && <FormError message={errorMessage(cancel.error)} />}
    </div>
  )
}

export function BookingsPage() {
  const bookings = useQuery({ queryKey: ['bookings'], queryFn: () => bookingApi.list() })
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="My requests"
        job="Every request with its current status and the reason the allocation gave it."
        actions={<Link to="/driver/stations" className={buttonClass('primary')}>New request</Link>}
      />
      <Panel title="Requests" aside={bookings.data && `${bookings.data.totalElements} total`} bodyClassName="">
        <QueryState
          query={bookings}
          rows={6}
          isEmpty={(page) => page.content.length === 0}
          empty={
            <EmptyState title="No requests yet">
              Find a station with a compatible connector and request a charging window. It stays pending until the next
              allocation run.
            </EmptyState>
          }
        >
          {(page) => (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Request</th>
                    <th scope="col">Status</th>
                    <th scope="col">Station</th>
                    <th scope="col">Window</th>
                    <th scope="col" className="text-right">Energy</th>
                    <th scope="col">Why</th>
                  </tr>
                </thead>
                <tbody>
                  {page.content.map((booking) => (
                    <tr key={booking.id}>
                      <td><Link to={`/driver/bookings/${booking.id}`} className="underline"><Mono>#{booking.id}</Mono></Link></td>
                      <td><StatusChip status={booking.status} /></td>
                      <td>{booking.stationName}</td>
                      <td className="whitespace-nowrap">{formatWindow(booking.requestedStart, booking.requestedEnd)}</td>
                      <td className="text-right">{formatKwh(booking.requestedEnergyKwh)}</td>
                      <td className="min-w-64 text-muted">{outcomeSentence(booking)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryState>
      </Panel>
    </div>
  )
}

export function BookingDetailPage() {
  const bookingId = Number(useParams().bookingId)
  const booking = useQuery({ queryKey: ['booking', bookingId], queryFn: () => bookingApi.get(bookingId) })
  return (
    <QueryState query={booking} rows={6}>
      {(data) => (
        <div className="flex flex-col gap-6">
          <div><Link to="/driver/bookings" className="text-sm text-muted underline hover:text-text">All requests</Link></div>
          <PageHeader
            title={`Request #${data.id}`}
            job={`${data.vehicleRegistration} at ${data.stationName}, ${formatWindow(data.requestedStart, data.requestedEnd)}`}
            badges={<StatusChip status={data.status} />}
          />
          <Panel title="Outcome">
            <p className="max-w-2xl text-lg">{outcomeSentence(data)}</p>
            {data.statusReason && (
              <p className="mt-2 text-sm text-muted">
                Reason code <Mono>{data.statusReason}</Mono> · {describeReason(data.statusReason).outcome === 'REJECTED'
                  ? 'This request cannot be served as submitted.'
                  : 'This request stays in the queue.'}
              </p>
            )}
            <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-4 text-sm sm:grid-cols-4">
              <div><dt className="text-muted">Requested energy</dt><dd className="tabular-nums">{formatKwh(data.requestedEnergyKwh)}</dd></div>
              <div><dt className="text-muted">Connector</dt><dd>{data.connectorId ? `${connectorLabel(data.connectorType)} #${data.connectorId}` : '—'}</dd></div>
              <div><dt className="text-muted">Allocated power</dt><dd className="tabular-nums">{formatKw(data.allocatedPowerKw)}</dd></div>
              <div><dt className="text-muted">Submitted</dt><dd>{formatDateTime(data.createdAt)}</dd></div>
            </dl>
            <div className="mt-6 flex flex-wrap items-start gap-3">
              {data.sessionId && (
                <Link to={`/driver/sessions/${data.sessionId}`} className={buttonClass('primary', 'sm')}>View session</Link>
              )}
              <CancelButton booking={data} />
            </div>
          </Panel>
        </div>
      )}
    </QueryState>
  )
}

export function SessionPage() {
  const sessionId = Number(useParams().sessionId)
  const session = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => sessionApi.get(sessionId),
    refetchInterval: 30_000,
  })
  return (
    <QueryState query={session} rows={6}>
      {(data) => (
        <div className="flex flex-col gap-6">
          <div><Link to={`/driver/bookings/${data.bookingId}`} className="text-sm text-muted underline hover:text-text">Request #{data.bookingId}</Link></div>
          <PageHeader
            title={`Session #${data.id}`}
            job={`${data.vehicleRegistration} at ${data.stationName}. The backend advances the session every minute.`}
          />
          <Panel><SessionProgress session={data} /></Panel>
        </div>
      )}
    </QueryState>
  )
}

export function HistoryPage() {
  const sessions = useQuery({ queryKey: ['sessions', 'history'], queryFn: () => sessionApi.history() })
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Session history"
        job="Your charging sessions, newest first, with the energy the simulation delivered."
        badges={<DataBadge kind="simulated" />}
      />
      <Panel title="Sessions" bodyClassName="">
        <QueryState
          query={sessions}
          rows={6}
          isEmpty={(page) => page.content.length === 0}
          empty={<EmptyState title="No sessions yet">A session appears here once allocation assigns one of your requests.</EmptyState>}
        >
          {(page) => (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Session</th>
                    <th scope="col">Status</th>
                    <th scope="col">Station</th>
                    <th scope="col">Window</th>
                    <th scope="col" className="text-right">Power</th>
                    <th scope="col" className="text-right">Delivered</th>
                  </tr>
                </thead>
                <tbody>
                  {page.content.map((session) => (
                    <tr key={session.id}>
                      <td><Link to={`/driver/sessions/${session.id}`} className="underline"><Mono>#{session.id}</Mono></Link></td>
                      <td><StatusChip status={session.status} /></td>
                      <td>{session.stationName}</td>
                      <td className="whitespace-nowrap">{formatWindow(session.windowStart, session.windowEnd)}</td>
                      <td className="text-right">{formatKw(session.allocatedPowerKw)}</td>
                      <td className="text-right whitespace-nowrap">{formatKwh(session.energyDeliveredKwh)} of {formatKwh(session.requestedEnergyKwh)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryState>
      </Panel>
    </div>
  )
}
