import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { ObjectiveStatement } from '../../components/ObjectiveStatement'
import { StatusChip } from '../../components/StatusChip'
import { Mono, Panel } from '../../components/ui'
import { formatInteger, formatKw, formatNs, formatRatio, humanise } from '../../lib/format'
import { describeReason } from '../../lib/reasons'
import type { AllocationRunResponse, StatusReason } from '../../types/api'

type Segment = { key: string; label: string; count: number; className: string }

/** Assigned, waitlisted and rejected as one full-width bar; the assigned count is the view's hero figure. */
function OutcomeBar({ assigned, waitlisted, rejected, eligible }: { assigned: number; waitlisted: number; rejected: number; eligible: number }) {
  const segments: Segment[] = [
    { key: 'assigned', label: 'Assigned', count: assigned, className: 'bg-accent' },
    { key: 'waitlisted', label: 'Waitlisted', count: waitlisted, className: 'bg-warning' },
    { key: 'rejected', label: 'Rejected', count: rejected, className: 'bg-danger' },
  ]
  const total = Math.max(1, assigned + waitlisted + rejected)
  return (
    <div className="flex flex-col gap-4">
      <dl className="flex flex-wrap items-start gap-x-10 gap-y-3">
        <div>
          <dt className="text-sm text-muted">Assigned</dt>
          <dd className="text-5xl font-semibold tabular-nums tracking-tight text-accent">{assigned}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted">Waitlisted</dt>
          <dd className="text-3xl font-semibold tabular-nums">{waitlisted}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted">Rejected</dt>
          <dd className="text-3xl font-semibold tabular-nums">{rejected}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted">Eligible requests</dt>
          <dd className="text-3xl font-semibold tabular-nums text-muted">{eligible}</dd>
        </div>
      </dl>
      <div aria-hidden="true" className="flex h-3 w-full gap-0.5 overflow-hidden rounded-sm bg-surface-raised">
        {segments.filter((segment) => segment.count > 0).map((segment) => (
          <div
            key={segment.key}
            title={`${segment.label}: ${segment.count}`}
            className={`${segment.className} origin-left animate-[grow_400ms_var(--ease-out-quint)]`}
            style={{ width: `${(segment.count / total) * 100}%` }}
          />
        ))}
      </div>
    </div>
  )
}

export function AllocationOutcome({ run, stationNames }: { run: AllocationRunResponse; stationNames: Map<number, string> }) {
  const { result } = run
  const [reasonFilter, setReasonFilter] = useState<StatusReason | null>(null)
  const ledgerHeading = useId()
  const stationName = (id: number) => stationNames.get(id) ?? `Station ${id}`

  const reasonCounts = new Map<StatusReason, number>()
  for (const item of result.unassigned) {
    reasonCounts.set(item.reason, (reasonCounts.get(item.reason) ?? 0) + 1)
  }
  const reasons = [...reasonCounts.entries()].sort((left, right) => right[1] - left[1])
  const bindingStations = new Set(
    result.bottlenecks.filter((item) => item.kind === 'STATION_GRID' && item.stationId !== null).map((item) => item.stationId),
  )
  const bookingBottleneck = new Map(
    result.bottlenecks.filter((item) => item.bookingId !== null).map((item) => [item.bookingId, item.kind]),
  )
  // Binding stations first, then by how far demand outruns the limit, so the constraint that shaped the run leads.
  const stations = [...result.stations].sort((left, right) =>
    Number(bindingStations.has(right.stationId)) - Number(bindingStations.has(left.stationId))
    || (right.demandCapacityRatio ?? -1) - (left.demandCapacityRatio ?? -1))
  const unmatchedReason = (stationId: number) => {
    const counts = new Map<StatusReason, number>()
    for (const item of result.unassigned) {
      if (item.stationId === stationId) {
        counts.set(item.reason, (counts.get(item.reason) ?? 0) + 1)
      }
    }
    const [top] = [...counts.entries()].sort((left, right) => right[1] - left[1])
    if (!top) {
      return 'No request matched'
    }
    const label = describeReason(top[0]).label
    return `No request matched: ${label.charAt(0).toLowerCase()}${label.slice(1)} (${top[1]})`
  }
  const selectReason = (reason: StatusReason, active: boolean) => {
    setReasonFilter(active ? null : reason)
    if (!active) {
      document.getElementById(ledgerHeading)?.closest('section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }
  const unassigned = reasonFilter ? result.unassigned.filter((item) => item.reason === reasonFilter) : result.unassigned
  const metrics = result.metrics

  return (
    <div className="flex flex-col gap-6">
      <OutcomeBar
        assigned={result.assignedCount}
        waitlisted={result.waitlistedCount}
        rejected={result.rejectedCount}
        eligible={result.eligibleRequests}
      />

      <div className="grid gap-6 xl:grid-cols-2 xl:items-start">
        <Panel title="Why requests were not assigned" aside="Select a reason to filter the ledger" bodyClassName="p-2">
          {reasons.length === 0 ? (
            <p className="p-3 text-sm text-muted">Every eligible request was assigned.</p>
          ) : (
            <ul className="flex flex-col">
              {reasons.map(([reason, count]) => {
                const text = describeReason(reason)
                const active = reasonFilter === reason
                return (
                  <li key={reason}>
                    <button
                      type="button"
                      aria-pressed={active}
                      onClick={() => selectReason(reason, active)}
                      className={`grid w-full grid-cols-[1fr_auto] items-start gap-x-4 rounded-md px-3 py-2.5 text-left transition-colors duration-150 ${
                        active ? 'bg-surface-raised ring-1 ring-border-strong' : 'hover:bg-surface-raised/60'
                      }`}
                    >
                      <span>
                        <span className="flex items-center gap-3">
                          <span className="font-medium">{text.label}</span>
                          {text.outcome !== 'UNKNOWN' && <StatusChip status={text.outcome} />}
                          {active && <span className="text-xs font-medium text-text">Filtering</span>}
                        </span>
                        <span className="mt-0.5 block text-sm text-muted">{text.sentence}</span>
                      </span>
                      <span className="text-xl font-semibold tabular-nums">{count}</span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </Panel>

        <Panel
          title="Station power"
          aside={bindingStations.size > 0 ? 'Allocated against the grid-import limit' : "No station's grid limit binds this run"}
          bodyClassName="p-0"
        >
          <ul aria-label="Station power" className="flex flex-col">
            {stations.map((station) => {
              const binds = bindingStations.has(station.stationId)
              const name = stationName(station.stationId)
              const scale = Math.max(station.gridKw, 1)
              const matched = station.allocatedKw !== null
              const committed = station.committedPeakKw ?? 0
              return (
                <li key={station.stationId} aria-label={name} className="border-b border-border px-5 py-3 last:border-b-0">
                  <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm">
                    <span className="font-medium">{name}</span>
                    <span className="tabular-nums text-muted">
                      {matched ? (
                        <><span className="text-text">{formatKw(station.allocatedKw)}</span> of {formatKw(station.gridKw)}</>
                      ) : (
                        <>{unmatchedReason(station.stationId)} · limit {formatKw(station.gridKw)}</>
                      )}
                      {committed > 0 && <> · {formatKw(committed)} already committed</>}
                    </span>
                  </div>
                  {/* The track is the grid limit; the bar is allocated power and the tick marks demand (pinned at the limit when it exceeds it). */}
                  <div aria-hidden="true" className="relative mt-2 h-2 w-full rounded-sm bg-surface-raised">
                    <div className="h-full rounded-r-[4px] bg-series-1" style={{ width: `${Math.min(100, (((station.allocatedKw ?? 0) + committed) / scale) * 100)}%` }} />
                    <div className="absolute -top-1 h-4 w-0.5 -translate-x-1/2 bg-text/70" style={{ left: `${Math.min(100, (station.demandKw / scale) * 100)}%` }} />
                  </div>
                  <div className="mt-1.5 flex flex-wrap gap-x-4 text-sm text-muted">
                    <span>Demand {formatKw(station.demandKw)} ({formatRatio(station.demandCapacityRatio)} of limit)</span>
                    {binds && <span className="font-medium text-warning">Grid limit binds</span>}
                  </div>
                </li>
              )
            })}
          </ul>
        </Panel>
      </div>

      <ObjectiveStatement />

      <Panel title="Algorithm evidence" bodyClassName="p-0">
        <dl className="grid grid-cols-2 text-sm sm:grid-cols-3 lg:grid-cols-6 [&>div]:border-b [&>div]:border-border [&>div]:px-5 [&>div]:py-3">
          <div><dt className="text-muted">Compatibility edges</dt><dd className="mt-0.5 tabular-nums">{formatInteger(metrics.compatibilityEdges)}</dd></div>
          <div><dt className="text-muted">Matching size</dt><dd className="mt-0.5 tabular-nums">{formatInteger(metrics.matchingCardinality)}</dd></div>
          <div><dt className="text-muted">Edges examined</dt><dd className="mt-0.5 tabular-nums">{formatInteger(metrics.edgesExamined)}</dd></div>
          <div><dt className="text-muted">Augmenting paths</dt><dd className="mt-0.5 tabular-nums">{formatInteger(metrics.augmentations)}</dd></div>
          <div><dt className="text-muted">Flow operations</dt><dd className="mt-0.5 tabular-nums">{formatInteger(metrics.flowOperations)}</dd></div>
          <div><dt className="text-muted">Total runtime</dt><dd className="mt-0.5 tabular-nums">{formatNs(metrics.totalNs)}</dd></div>
          <div><dt className="text-muted">Requested power</dt><dd className="mt-0.5 tabular-nums">{formatKw(metrics.totalRequestedKw)}</dd></div>
          <div><dt className="text-muted">Allocated power</dt><dd className="mt-0.5 tabular-nums">{formatKw(metrics.totalAllocatedKw)}</dd></div>
          <div><dt className="text-muted">Unserved power</dt><dd className="mt-0.5 tabular-nums">{formatKw(metrics.unservedKw)}</dd></div>
          <div>
            <dt className="text-muted">Requested-power p50 / p75 / p90</dt>
            <dd className="mt-0.5 tabular-nums">
              {result.requestedPower
                ? `${formatKw(result.requestedPower.p50Kw)} / ${formatKw(result.requestedPower.p75Kw)} / ${formatKw(result.requestedPower.p90Kw)}`
                : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-muted">Quickselect work</dt>
            <dd className="mt-0.5 tabular-nums">
              {result.requestedPower
                ? `${formatInteger(result.requestedPower.comparisons)} comparisons, seed ${result.requestedPower.seed}`
                : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-muted">Matching / power / selection</dt>
            <dd className="mt-0.5 tabular-nums">{formatNs(metrics.matchingNs)} / {formatNs(metrics.powerNs)} / {formatNs(metrics.selectionNs)}</dd>
          </div>
        </dl>
        <p className="px-5 py-3 text-xs text-muted">
          {humanise(result.matchingAlgorithm)}, {humanise(result.powerAlgorithm)} and {humanise(result.selectionAlgorithm)}.
          A connector receives at most one new booking per allocation run.{' '}
          <Link to="/operator/lab" className="text-text underline">Compare each algorithm with its greedy baseline in the Algorithm Lab.</Link>
        </p>
      </Panel>

      <div className="grid gap-6">
        <Panel title="Assignments" aside={`${result.assignments.length} sessions`} bodyClassName="max-h-[28rem] overflow-auto">
          <table className="data-table" aria-label="Assignments">
            <thead>
              <tr>
                <th scope="col">Request</th>
                <th scope="col">Station</th>
                <th scope="col">Connector</th>
                <th scope="col" className="text-right">Requested</th>
                <th scope="col" className="text-right">Allocated</th>
                <th scope="col">Limited by</th>
              </tr>
            </thead>
            <tbody>
              {result.assignments.map((item) => (
                <tr key={item.bookingId}>
                  <td><Mono>#{item.bookingId}</Mono></td>
                  <td className="whitespace-nowrap">{stationName(item.stationId)}</td>
                  <td><Mono>#{item.connectorId}</Mono></td>
                  <td className="text-right">{formatKw(item.requestedKw)}</td>
                  <td className="text-right">{formatKw(item.allocatedKw)}</td>
                  <td className="text-muted">
                    {bindingStations.has(item.stationId) && item.allocatedKw < item.requestedKw
                      ? 'Station grid'
                      : humanise(bookingBottleneck.get(item.bookingId) ?? 'SESSION_DEMAND')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>

        <Panel
          labelledBy={ledgerHeading}
          title="Unassigned requests"
          aside={reasonFilter ? `${describeReason(reasonFilter).label} only` : `${result.unassigned.length} requests`}
          bodyClassName="max-h-[28rem] overflow-auto"
        >
          <table className="data-table" aria-label="Unassigned requests">
            <thead>
              <tr>
                <th scope="col">Request</th>
                <th scope="col">Station</th>
                <th scope="col">Outcome</th>
                <th scope="col">Reason</th>
              </tr>
            </thead>
            <tbody>
              {unassigned.map((item) => (
                <tr key={item.bookingId}>
                  <td><Mono>#{item.bookingId}</Mono></td>
                  <td className="whitespace-nowrap">{stationName(item.stationId)}</td>
                  <td><StatusChip status={item.status} /></td>
                  <td>{describeReason(item.reason).label}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      </div>
    </div>
  )
}
