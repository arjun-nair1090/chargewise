import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiErrorAlert } from '../../components/ApiErrorAlert'
import { CompareBars } from '../../components/CompareBars'
import { TextField, SelectField } from '../../components/Field'
import { ObjectiveStatement } from '../../components/ObjectiveStatement'
import { QueryState } from '../../components/QueryState'
import { DataBadge, StatusChip } from '../../components/StatusChip'
import { Button, EmptyState, Mono, PageHeader, Panel } from '../../components/ui'
import { formatInteger, formatKw, formatKwh, formatNs, humanise } from '../../lib/format'
import { SCENARIO_TEXT } from '../../lib/scenarios'
import { simulationApi } from '../../services/endpoints'
import {
  SCENARIOS,
  type ComparisonRow,
  type Scenario,
  type SimulationComparisonResponse,
  type SimulationRequest,
  type SimulationResponse,
} from '../../types/api'

const DEFAULT_SEED = 20261102
const SHAPE_FIELDS = [
  { key: 'stations', label: 'Stations', max: 100 },
  { key: 'connectorsPerStation', label: 'Connectors per station', max: 16 },
  { key: 'requests', label: 'Requests', max: 5000 },
  { key: 'horizonSlots', label: 'Horizon (15-minute slots)', max: 192 },
  { key: 'maxWaitSlots', label: 'Longest wait (slots)', max: 32 },
] as const
type ShapeKey = (typeof SHAPE_FIELDS)[number]['key']

const MINUS = '−'

function formatValue(value: number | null, unit: string): string {
  if (value === null) {
    return '—'
  }
  switch (unit) {
    case 'kW':
      return formatKw(value)
    case 'kWh':
      return formatKwh(value)
    case 'min':
      return `${formatInteger(value)} min`
    case 'ratio':
      return `${(value * 100).toFixed(1)}%`
    default:
      return formatInteger(value)
  }
}

/** Signed algorithm-minus-baseline difference, with who is ahead in words so the sign never has to be decoded. */
function formatDifference(row: ComparisonRow): string {
  if (row.difference === null) {
    return '—'
  }
  if (row.difference === 0) {
    return '0 (same)'
  }
  const magnitude = Math.abs(row.difference)
  const amount = row.unit === 'ratio' ? `${(magnitude * 100).toFixed(1)} pp` : formatValue(magnitude, row.unit)
  const algorithmAhead = (row.difference > 0) === (row.betterWhen === 'HIGHER')
  return `${row.difference > 0 ? '+' : MINUS}${amount} (${algorithmAhead ? 'algorithm' : 'baseline'} ahead)`
}

function ScenarioForm({ onRun, busy, error }: {
  onRun: (request: SimulationRequest) => void
  busy: boolean
  error: unknown
}) {
  const [scenario, setScenario] = useState<Scenario>('NORMAL')
  const [name, setName] = useState('')
  const [seed, setSeed] = useState(String(DEFAULT_SEED))
  const [shape, setShape] = useState<Record<ShapeKey, string>>({
    stations: '', connectorsPerStation: '', requests: '', horizonSlots: '', maxWaitSlots: '',
  })

  const submit = () => {
    const request: SimulationRequest = {
      name: name.trim() || SCENARIO_TEXT[scenario].label,
      scenario,
      seed: Number(seed),
    }
    for (const field of SHAPE_FIELDS) {
      if (shape[field.key].trim() !== '') {
        request[field.key] = Number(shape[field.key])
      }
    }
    onRun(request)
  }

  return (
    <Panel title="1. Scenario" bodyClassName="p-5">
      <form noValidate onSubmit={(event) => { event.preventDefault(); submit() }} className="flex flex-col gap-4">
        <SelectField label="Scenario" value={scenario} onChange={(event) => setScenario(event.target.value as Scenario)} hint={SCENARIO_TEXT[scenario].description}>
          {SCENARIOS.map((value) => <option key={value} value={value}>{SCENARIO_TEXT[value].label}</option>)}
        </SelectField>
        <TextField label="Name" placeholder={SCENARIO_TEXT[scenario].label} value={name} onChange={(event) => setName(event.target.value)} />
        <TextField
          label="Seed"
          inputMode="numeric"
          value={seed}
          onChange={(event) => setSeed(event.target.value)}
          hint="The same scenario, shape and seed always generate the same input."
        />
        <details className="text-sm">
          <summary className="cursor-pointer text-muted hover:text-text">Shape (blank fields use the preset)</summary>
          <div className="mt-3 grid grid-cols-2 gap-3">
            {SHAPE_FIELDS.map((field) => (
              <TextField
                key={field.key}
                label={field.label}
                inputMode="numeric"
                placeholder="Preset"
                hint={`Up to ${formatInteger(field.max)}`}
                value={shape[field.key]}
                onChange={(event) => setShape((current) => ({ ...current, [field.key]: event.target.value }))}
              />
            ))}
          </div>
        </details>
        <ApiErrorAlert error={error} />
        <Button type="submit" variant="primary" busy={busy}>Run simulation</Button>
      </form>
    </Panel>
  )
}

function Comparison({ data }: { data: SimulationComparisonResponse }) {
  const { algorithm, baseline } = data
  const waits = algorithm.waitP50Minutes !== null && baseline.waitP50Minutes !== null
  return (
    <div className="flex flex-col gap-6">
      <p role="note" aria-label="Per-slot evidence" className="rounded-md border border-border-strong px-4 py-3 text-sm">
        In no slot did greedy matching start more requests than bipartite matching on the identical graph
        {algorithm.slotsWhereBaselineMatchedMore === 0 ? '' : ` (${algorithm.slotsWhereBaselineMatchedMore} slots did)`};
        across the day matching started {formatInteger(algorithm.matchedGainOverBaseline)} more in total, slot by slot.
        In no slot did the greedy allocator grant more power than Edmonds-Karp on the identical network
        {algorithm.slotsWhereBaselineAllocatedMore === 0 ? '' : ` (${algorithm.slotsWhereBaselineAllocatedMore} slots did)`};
        Edmonds-Karp granted {formatKw(algorithm.allocatedGainOverBaselineKw)} more, summed over slots.
      </p>

      <div className="flex flex-col gap-5">
        <CompareBars
          title="Requests started"
          algorithm={{ name: humanise(algorithm.matchingAlgorithm), value: algorithm.matchedCount }}
          baseline={{ name: humanise(baseline.matchingAlgorithm), value: baseline.matchedCount }}
          format={formatInteger}
        />
        <CompareBars
          title="Energy delivered"
          algorithm={{ name: humanise(algorithm.powerAlgorithm), value: algorithm.deliveredEnergyKwh }}
          baseline={{ name: humanise(baseline.powerAlgorithm), value: baseline.deliveredEnergyKwh }}
          format={formatKwh}
        />
        {waits && (
          <CompareBars
            title="Median wait to start"
            algorithm={{ name: 'Matching + Edmonds-Karp', value: algorithm.waitP50Minutes ?? 0 }}
            baseline={{ name: 'Greedy + greedy', value: baseline.waitP50Minutes ?? 0 }}
            format={(value) => `${formatInteger(value)} min`}
            higherIsBetter={false}
          />
        )}
      </div>

      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="data-table" aria-label="Algorithm against baseline">
          <thead>
            <tr>
              <th scope="col">Measure</th>
              <th scope="col" className="text-right">Algorithms</th>
              <th scope="col" className="text-right">Baselines</th>
              <th scope="col" className="text-right">Difference</th>
            </tr>
          </thead>
          <tbody>
            {data.rows.map((row) => (
              <tr key={row.metric}>
                <th scope="row" className="font-medium text-text">{row.label}</th>
                <td className="text-right">{formatValue(row.algorithm, row.unit)}</td>
                <td className="text-right">{formatValue(row.baseline, row.unit)}</td>
                <td className="text-right text-muted">{formatDifference(row)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
        <div><dt className="text-muted">Edges examined</dt><dd className="tabular-nums">{formatInteger(algorithm.edgesExamined)} / {formatInteger(baseline.edgesExamined)}</dd></div>
        <div><dt className="text-muted">Power operations</dt><dd className="tabular-nums">{formatInteger(algorithm.powerOperations)} / {formatInteger(baseline.powerOperations)}</dd></div>
        <div><dt className="text-muted">Matching time</dt><dd className="tabular-nums">{formatNs(algorithm.matchingNs)} / {formatNs(baseline.matchingNs)}</dd></div>
        <div><dt className="text-muted">Power time</dt><dd className="tabular-nums">{formatNs(algorithm.powerNs)} / {formatNs(baseline.powerNs)}</dd></div>
      </dl>
      <p className="text-xs text-muted">
        Pairs read algorithms / baselines. Times are one wall-clock run on this server, not a benchmark. Day totals come
        from two different histories, so they are measured outcomes; only the per-slot comparison is guaranteed.
      </p>
    </div>
  )
}

function SimulationResult({ simulation }: { simulation: SimulationResponse }) {
  const client = useQueryClient()
  const [verdict, setVerdict] = useState<boolean | null>(null)
  const comparison = useQuery({
    queryKey: ['simulation-comparison', simulation.id],
    queryFn: () => simulationApi.comparison(simulation.id),
    enabled: simulation.status === 'COMPLETED',
  })
  const rerun = useMutation({
    mutationFn: () => simulationApi.run(simulation.id),
    onSuccess: (response) => {
      setVerdict(response.reproducedPreviousRun ?? null)
      void client.invalidateQueries({ queryKey: ['simulations'] })
      void client.invalidateQueries({ queryKey: ['simulation-comparison', simulation.id] })
    },
  })
  const { configuration } = simulation

  return (
    <Panel
      title={`2. ${simulation.name}`}
      aside={<StatusChip status={simulation.status} />}
      bodyClassName="flex flex-col gap-5 p-5"
    >
      <p className="text-sm text-muted">
        {SCENARIO_TEXT[configuration.scenario].label}: {formatInteger(configuration.stations)} stations of{' '}
        {formatInteger(configuration.connectorsPerStation)} connectors, {formatInteger(configuration.requests)} requests over{' '}
        {formatInteger(configuration.horizonSlots)} slots, waits of up to {formatInteger(configuration.maxWaitSlots)} slots.
        Seed <Mono>{simulation.seed}</Mono>.
      </p>
      {simulation.status !== 'COMPLETED' ? (
        <div className="flex flex-col items-start gap-3">
          <p className="text-sm">This simulation has not run yet.</p>
          <Button variant="primary" busy={rerun.isPending} onClick={() => rerun.mutate()}>Run simulation</Button>
        </div>
      ) : (
        <QueryState query={comparison} rows={6}>
          {(data) => (
            <div className="flex flex-col gap-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-muted">
                  Input SHA-256 <span title={data.inputHash}><Mono>{data.inputHash.slice(0, 12)}…</Mono></span>, shared by both runs.
                </p>
                <Button busy={rerun.isPending} onClick={() => rerun.mutate()}>Re-run and verify</Button>
              </div>
              {verdict !== null && (
                <p role="status" className={`text-sm ${verdict ? 'text-text' : 'text-danger'}`}>
                  {verdict
                    ? 'Reproduced: identical input hash and identical results (runtimes excluded).'
                    : 'Not reproduced: the input or a result differs from the stored run.'}
                </p>
              )}
              <Comparison data={data} />
            </div>
          )}
        </QueryState>
      )}
      <ApiErrorAlert error={rerun.error} />
    </Panel>
  )
}

export function SimulationsPage() {
  const client = useQueryClient()
  const [chosenId, setSelectedId] = useState<number | null>(null)
  const simulations = useQuery({ queryKey: ['simulations'], queryFn: simulationApi.list })
  // Until one is chosen, show the newest completed simulation, so the page opens on a comparison.
  const selectedId = chosenId ?? simulations.data?.find((item) => item.status === 'COMPLETED')?.id ?? null
  const createAndRun = useMutation({
    mutationFn: async (request: SimulationRequest) => {
      const created = await simulationApi.create(request)
      return simulationApi.run(created.id)
    },
    onSuccess: (response) => {
      setSelectedId(response.simulation.id)
      client.setQueryData<SimulationResponse[]>(['simulations'], (current = []) =>
        [response.simulation, ...current.filter((item) => item.id !== response.simulation.id)])
      void client.invalidateQueries({ queryKey: ['simulations'] })
    },
  })
  const selected = simulations.data?.find((item) => item.id === selectedId) ?? null

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Simulations"
        job="Run one seeded scenario twice on the identical input, once with the algorithms and once with the greedy baselines, and compare."
        badges={<DataBadge kind="simulated" />}
      />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] xl:items-start">
        <div className="flex flex-col gap-6">
          <ScenarioForm onRun={(request) => createAndRun.mutate(request)} busy={createAndRun.isPending} error={createAndRun.error} />
          <Panel title="Recent simulations" bodyClassName="p-2">
            <QueryState query={simulations} rows={3}>
              {(items) => items.length === 0 ? (
                <p className="p-3 text-sm text-muted">No simulations yet.</p>
              ) : (
                <ul className="flex flex-col">
                  {items.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        aria-pressed={item.id === selectedId}
                        onClick={() => setSelectedId(item.id)}
                        className={`flex w-full flex-wrap items-baseline justify-between gap-x-3 rounded-md px-3 py-2 text-left text-sm transition-colors duration-150 ${
                          item.id === selectedId ? 'bg-surface-raised ring-1 ring-border-strong' : 'hover:bg-surface-raised/60'
                        }`}
                      >
                        <span className="font-medium">{item.name}</span>
                        <span className="text-muted">{SCENARIO_TEXT[item.configuration.scenario].label} · seed {item.seed}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </QueryState>
          </Panel>
        </div>
        {selected ? (
          <SimulationResult key={selected.id} simulation={selected} />
        ) : (
          <Panel bodyClassName="p-0">
            <EmptyState title="No simulation selected">
              Run a scenario, or pick a recent one, to compare the algorithms with the baselines on the same input.
            </EmptyState>
          </Panel>
        )}
      </div>
      <ObjectiveStatement />
    </div>
  )
}
