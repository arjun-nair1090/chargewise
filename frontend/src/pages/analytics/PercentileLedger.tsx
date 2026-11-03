import { Panel } from '../../components/ui'
import { formatDuration, formatInteger, formatKw, humanise } from '../../lib/format'
import type { AllocationRunResult, DashboardResponse } from '../../types/api'

/**
 * Every server-side percentile as one ledger: a row per measure, a column per rank. The median wait is the
 * view's hero figure; ranks the backend does not report show a dash rather than an invented value.
 */
export function PercentileLedger({ waiting, power, runId }: {
  waiting: DashboardResponse['waiting']
  power: AllocationRunResult['requestedPower'] | undefined
  runId: number | null
}) {
  return (
    <Panel title="Percentiles" aside="Nearest-rank, selected on the server" bodyClassName="p-0">
      <div className="overflow-x-auto">
        <table className="data-table" aria-label="Percentiles">
          <thead>
            <tr>
              <th scope="col">Measure</th>
              <th scope="col" className="text-right">p50</th>
              <th scope="col" className="text-right">p75</th>
              <th scope="col" className="text-right">p90</th>
              <th scope="col">Sample</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th scope="row" className="font-medium text-text">Wait to assignment</th>
              <td className="text-right text-lg font-semibold text-accent">{formatDuration(waiting.p50AssignWaitSeconds)}</td>
              <td className="text-right text-muted">—</td>
              <td className="text-right">{formatDuration(waiting.p90AssignWaitSeconds)}</td>
              <td className="text-muted">{formatInteger(waiting.assignedSampleSize)} assigned</td>
            </tr>
            <tr>
              <th scope="row" className="font-medium text-text">Queue age</th>
              <td className="text-right">{formatDuration(waiting.p50QueueAgeSeconds)}</td>
              <td className="text-right text-muted">—</td>
              <td className="text-right">{formatDuration(waiting.p90QueueAgeSeconds)}</td>
              <td className="text-muted">{formatInteger(waiting.queueSize)} queued</td>
            </tr>
            <tr>
              <th scope="row" className="font-medium text-text">Requested power</th>
              <td className="text-right">{power ? formatKw(power.p50Kw) : '—'}</td>
              <td className="text-right">{power ? formatKw(power.p75Kw) : '—'}</td>
              <td className="text-right">{power ? formatKw(power.p90Kw) : '—'}</td>
              <td className="text-muted">{runId === null ? 'No run yet' : `Run #${runId}`}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p className="border-t border-border px-5 py-3 text-sm text-muted">
        Selected by {humanise(waiting.algorithm)}
        {power && <>; the power percentiles took {formatInteger(power.comparisons)} comparisons, {formatInteger(power.swaps)} swaps, seed {power.seed}</>}.
        Waiting time is assignment time minus request time; queue age is how long a pending request has waited so far.
      </p>
    </Panel>
  )
}
