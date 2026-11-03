import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { ApiErrorAlert } from '../../components/ApiErrorAlert'
import { TextField } from '../../components/Field'
import { ObjectiveStatement } from '../../components/ObjectiveStatement'
import { QueryState } from '../../components/QueryState'
import { DataBadge, StatusChip } from '../../components/StatusChip'
import { Button, EmptyState, Mono, PageHeader, Panel, Spinner } from '../../components/ui'
import { formatInteger, formatKw, formatNanos, humanise } from '../../lib/format'
import { benchmarkApi } from '../../services/endpoints'
import {
  BENCHMARK_FAMILIES,
  BENCHMARK_SIZES,
  type BenchmarkCase,
  type BenchmarkFamily,
  type BenchmarkMeasurement,
  type BenchmarkReport,
  type BenchmarkRequest,
  type BenchmarkRunResponse,
  type BenchmarkSize,
  type RunStatus,
} from '../../types/api'

const DEFAULT_SEED = 20261103
const POLL_MS = 1000

const SIZE_TEXT: Record<BenchmarkSize, string> = {
  SMALL: '100 requests, 30 connectors, 1,000 values',
  MEDIUM: '1,000 requests, 300 connectors, 10,000 values',
  LARGE: '10,000 requests, 3,000 connectors, 100,000 values; about a minute',
}

const FAMILY_TEXT: Record<BenchmarkFamily, { title: string; algorithm: string; baseline: string; result: string }> = {
  MATCHING: { title: 'Bipartite matching against greedy first-available', algorithm: 'Bipartite matching', baseline: 'Greedy', result: 'Result' },
  POWER: { title: 'Edmonds-Karp against greedy priority', algorithm: 'Edmonds-Karp', baseline: 'Greedy', result: 'Power allocated' },
  POWER_FEEDERS: { title: 'Edmonds-Karp against greedy priority, with shared feeders', algorithm: 'Edmonds-Karp', baseline: 'Greedy', result: 'Power allocated' },
  SELECTION: { title: 'Randomized Quickselect against full sort', algorithm: 'Quickselect', baseline: 'Full sort', result: 'Result' },
  RANGE_QUERY: { title: 'Segment tree against naive scan', algorithm: 'Segment tree', baseline: 'Naive scan', result: 'Result' },
}

function isActive(status: RunStatus | undefined): boolean {
  return status === 'PENDING' || status === 'RUNNING'
}

function sizeList(sizes: BenchmarkSize[]): string {
  return sizes.map(humanise).join(', ')
}

function CheckboxGroup<T extends string>({ legend, options, selected, onToggle, describe, hint }: {
  legend: string
  options: readonly T[]
  selected: T[]
  onToggle: (value: T) => void
  describe: (value: T) => { label: string; detail?: string }
  hint?: string
}) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1.5 text-sm font-medium">{legend}</legend>
      {options.map((value) => {
        const { label, detail } = describe(value)
        return (
          <label key={value} className="flex cursor-pointer items-start gap-2.5 text-sm">
            <input
              type="checkbox"
              checked={selected.includes(value)}
              onChange={() => onToggle(value)}
              className="mt-0.5 size-4 accent-[var(--color-accent)]"
            />
            <span className="flex flex-col">
              <span>{label}</span>
              {detail && <span className="text-xs text-muted">{detail}</span>}
            </span>
          </label>
        )
      })}
      {hint && <p className="text-xs text-danger">{hint}</p>}
    </fieldset>
  )
}

function toggle<T>(list: T[], value: T, order: readonly T[]): T[] {
  const next = list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
  return order.filter((item) => next.includes(item))
}

function BenchmarkForm({ onRun, busy, error }: {
  onRun: (request: BenchmarkRequest) => void
  busy: boolean
  error: unknown
}) {
  const [seed, setSeed] = useState(String(DEFAULT_SEED))
  const [sizes, setSizes] = useState<BenchmarkSize[]>(['SMALL'])
  const [families, setFamilies] = useState<BenchmarkFamily[]>([...BENCHMARK_FAMILIES])
  const [trials, setTrials] = useState('7')
  const [warmups, setWarmups] = useState('3')
  const ready = sizes.length > 0 && families.length > 0

  const submit = () => {
    onRun({ seed: Number(seed), warmups: Number(warmups), trials: Number(trials), sizes, families })
  }

  return (
    <Panel title="1. Configure" bodyClassName="p-5">
      <form noValidate onSubmit={(event) => { event.preventDefault(); submit() }} className="flex flex-col gap-5">
        <TextField
          label="Seed"
          inputMode="numeric"
          value={seed}
          onChange={(event) => setSeed(event.target.value)}
          hint="Every input derives from the seed alone, so the same seed reproduces every non-timing result."
        />
        <CheckboxGroup
          legend="Sizes"
          options={BENCHMARK_SIZES}
          selected={sizes}
          onToggle={(value) => setSizes((current) => toggle(current, value, BENCHMARK_SIZES))}
          describe={(value) => ({ label: humanise(value), detail: SIZE_TEXT[value] })}
          hint={sizes.length === 0 ? 'Choose at least one size.' : undefined}
        />
        <CheckboxGroup
          legend="Algorithms"
          options={BENCHMARK_FAMILIES}
          selected={families}
          onToggle={(value) => setFamilies((current) => toggle(current, value, BENCHMARK_FAMILIES))}
          describe={(value) => ({ label: FAMILY_TEXT[value].title })}
          hint={families.length === 0 ? 'Choose at least one algorithm.' : undefined}
        />
        <div className="grid grid-cols-2 gap-3">
          <TextField label="Timed trials" inputMode="numeric" value={trials} onChange={(event) => setTrials(event.target.value)} hint="1 to 31" />
          <TextField label="Warm-up runs" inputMode="numeric" value={warmups} onChange={(event) => setWarmups(event.target.value)} hint="0 to 10, untimed" />
        </div>
        <ApiErrorAlert error={error} />
        <Button type="submit" variant="primary" busy={busy} disabled={!ready}>Run benchmark</Button>
      </form>
    </Panel>
  )
}

function caseLabel(item: BenchmarkCase): { title: string; detail: string } {
  const size = humanise(item.size)
  const p = item.parameters
  switch (item.family) {
    case 'MATCHING':
      return { title: `${size} · density ${p.density}`, detail: `${formatInteger(Number(p.edges))} edges` }
    case 'POWER':
      return { title: `${size} · ${p.stations} stations`, detail: `${formatInteger(Number(p.sessions))} sessions` }
    case 'POWER_FEEDERS':
      return {
        title: `${size} · ${p.stations} stations`,
        detail: `${formatInteger(Number(p.sessions))} sessions, ${p.feeders} feeders`,
      }
    case 'SELECTION':
      return { title: `${size} · ${humanise(String(p.distribution)).toLowerCase()}`, detail: `median of ${formatInteger(Number(p.n))}` }
    case 'RANGE_QUERY':
      return {
        title: `${size} · ${formatInteger(Number(p.n))} values`,
        detail: `${formatInteger(Number(p.queries))} queries, ${formatInteger(Number(p.updates))} updates`,
      }
  }
}

function formatRatio(ratio: number): string {
  return `${ratio < 10 ? ratio.toFixed(1) : formatInteger(Math.round(ratio))}×`
}

/** Which side was faster at the median, and whether the interquartile ranges overlap (then the gap is within noise). */
function timeVerdict(algorithm: BenchmarkMeasurement, baseline: BenchmarkMeasurement): ReactNode {
  const overlap = algorithm.q1Ns <= baseline.q3Ns && baseline.q1Ns <= algorithm.q3Ns
  const verdict = algorithm.medianNs === baseline.medianNs
    ? 'Same median'
    : algorithm.medianNs < baseline.medianNs
      ? `Algorithm ${formatRatio(baseline.medianNs / algorithm.medianNs)} faster`
      : `Baseline ${formatRatio(algorithm.medianNs / baseline.medianNs)} faster`
  return (
    <>
      <span className="whitespace-nowrap">{verdict}</span>
      {overlap && <span className="block text-xs text-muted">Spreads overlap</span>}
    </>
  )
}

function qualityText(item: BenchmarkCase): string {
  const a = item.algorithmQuality
  const b = item.baselineQuality
  switch (item.family) {
    case 'MATCHING':
      return a === b ? `Both ${formatInteger(a)} ${item.qualityUnit}` : `${formatInteger(a)} vs ${formatInteger(b)} ${item.qualityUnit} (algorithm +${formatInteger(a - b)})`
    case 'POWER':
    case 'POWER_FEEDERS':
      return a === b
        ? `Both ${formatKw(a / 1000)}`
        : `${formatKw(a / 1000)} vs ${formatKw(b / 1000)} (algorithm +${formatKw((a - b) / 1000)})`
    case 'SELECTION':
      return `Same value: ${formatInteger(a)}`
    case 'RANGE_QUERY':
      return 'Identical answers'
  }
}

function TimeCell({ measurement }: { measurement: BenchmarkMeasurement }) {
  return (
    <td className="text-right">
      <span className="block">{formatNanos(measurement.medianNs)}</span>
      <span className="block text-xs text-muted">IQR {formatNanos(measurement.iqrNs)}</span>
    </td>
  )
}

function FamilyTable({ family, cases }: { family: BenchmarkFamily; cases: BenchmarkCase[] }) {
  const text = FAMILY_TEXT[family]
  return (
    <section className="flex flex-col gap-3">
      <h3 className="text-sm font-semibold">{text.title}</h3>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="data-table" aria-label={text.title}>
          <thead>
            <tr>
              <th scope="col">Case</th>
              <th scope="col" className="text-right">{text.algorithm}</th>
              <th scope="col" className="text-right">{text.baseline}</th>
              <th scope="col">Time</th>
              <th scope="col">{text.result}</th>
            </tr>
          </thead>
          <tbody>
            {cases.map((item) => {
              const label = caseLabel(item)
              return (
                <tr key={item.label}>
                  <th scope="row" className="min-w-36 font-medium text-text">
                    <span className="block">{label.title}</span>
                    <span className="block text-xs font-normal text-muted">{label.detail}</span>
                  </th>
                  {item.correct && item.algorithm && item.baseline ? (
                    <>
                      <TimeCell measurement={item.algorithm} />
                      <TimeCell measurement={item.baseline} />
                      <td>{timeVerdict(item.algorithm, item.baseline)}</td>
                      <td className="min-w-48">{qualityText(item)}</td>
                    </>
                  ) : (
                    <td colSpan={4} className="text-danger">Verification failed, so this case was not timed.</td>
                  )}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function download(run: BenchmarkRunResponse, report: BenchmarkReport) {
  const blob = new Blob([JSON.stringify({ ...run, report }, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `chargewise-benchmark-${run.id}.json`
  link.click()
  URL.revokeObjectURL(url)
}

function Report({ run, report }: { run: BenchmarkRunResponse; report: BenchmarkReport }) {
  const { environment } = report
  const families = BENCHMARK_FAMILIES.filter((family) => report.cases.some((item) => item.family === family))
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <p className="max-w-prose text-sm text-muted">
          Java {environment.javaVersion} ({environment.vmName}) on {environment.osName}, {environment.availableProcessors}{' '}
          processors; the whole run took {formatNanos(report.totalNs)}.
        </p>
        <Button onClick={() => download(run, report)}>Download JSON</Button>
      </div>
      <p className="max-w-prose text-xs text-muted">
        Medians of {report.trials} timed {report.trials === 1 ? 'trial' : 'trials'} after {report.warmups} untimed
        warm-up {report.warmups === 1 ? 'run' : 'runs'}, by wall clock inside the running server. Both sides of a case
        get the identical input, and both answers are verified before either is timed. Results show what each side
        achieved on that input. The JMH suite gives the controlled measurement.
      </p>
      {families.map((family) => (
        <FamilyTable key={family} family={family} cases={report.cases.filter((item) => item.family === family)} />
      ))}
    </div>
  )
}

function BenchmarkResult({ runId }: { runId: number }) {
  const run = useQuery({
    queryKey: ['benchmark', runId],
    queryFn: () => benchmarkApi.get(runId),
    refetchInterval: (query) => (isActive(query.state.data?.status) ? POLL_MS : false),
  })
  const status = run.data?.status

  return (
    <Panel title={`2. Run ${runId}`} aside={status && <StatusChip status={status} />} bodyClassName="flex flex-col gap-5 p-5">
      <QueryState query={run} rows={6}>
        {(data) => (
          <div className="flex flex-col gap-5">
            <p className="text-sm text-muted">
              Seed <Mono>{data.seed}</Mono> · {sizeList(data.configuration.sizes)} · {data.configuration.trials} timed{' '}
              {data.configuration.trials === 1 ? 'trial' : 'trials'} after {data.configuration.warmups} warm-up{' '}
              {data.configuration.warmups === 1 ? 'run' : 'runs'}.
            </p>
            {isActive(data.status) && (
              <p role="status" className="flex items-center gap-2.5 text-sm">
                <Spinner />
                {data.status === 'PENDING'
                  ? 'Queued. Runs take turns on one benchmark thread so they never distort each other.'
                  : 'Measuring. This page updates when the run completes.'}
              </p>
            )}
            {data.status === 'FAILED' && (
              <p role="status" className="text-sm text-danger">
                This run failed before it finished, so it has no results. The server log names the cause; start a new run.
              </p>
            )}
            {data.status === 'COMPLETED' && data.report && <Report run={data} report={data.report} />}
          </div>
        )}
      </QueryState>
    </Panel>
  )
}

export function BenchmarksPage() {
  const client = useQueryClient()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const runs = useQuery({
    queryKey: ['benchmarks'],
    queryFn: benchmarkApi.list,
    refetchInterval: (query) => (query.state.data?.some((item) => isActive(item.status)) ? POLL_MS * 2 : false),
  })
  const start = useMutation({
    mutationFn: benchmarkApi.run,
    onSuccess: (response) => {
      setSelectedId(response.id)
      void client.invalidateQueries({ queryKey: ['benchmarks'] })
    },
  })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Benchmarks"
        job="Time each algorithm against its baseline on identical seeded inputs, after verifying that both answers are correct."
        badges={<><DataBadge kind="generated" /><DataBadge kind="measured" /></>}
      />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] xl:items-start">
        <div className="flex flex-col gap-6">
          <BenchmarkForm onRun={(request) => start.mutate(request)} busy={start.isPending} error={start.error} />
          <Panel title="Recent runs" bodyClassName="p-2">
            <QueryState query={runs} rows={3}>
              {(items) => items.length === 0 ? (
                <p className="p-3 text-sm text-muted">No benchmark runs yet.</p>
              ) : (
                <ul className="flex flex-col">
                  {items.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        aria-pressed={item.id === selectedId}
                        onClick={() => setSelectedId(item.id)}
                        className={`flex w-full flex-wrap items-center justify-between gap-x-3 gap-y-1 rounded-md px-3 py-2 text-left text-sm transition-colors duration-150 ${
                          item.id === selectedId ? 'bg-surface-raised ring-1 ring-border-strong' : 'hover:bg-surface-raised/60'
                        }`}
                      >
                        <span>
                          <span className="font-medium">Run {item.id}</span>
                          <span className="text-muted"> · Seed {item.seed} · {sizeList(item.configuration.sizes)}</span>
                        </span>
                        <StatusChip status={item.status} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </QueryState>
          </Panel>
        </div>
        {selectedId !== null ? (
          <BenchmarkResult key={selectedId} runId={selectedId} />
        ) : (
          <Panel bodyClassName="p-0">
            <EmptyState title="No run selected">
              Start a run, or pick a recent one, to see each algorithm timed against its baseline.
            </EmptyState>
          </Panel>
        )}
      </div>
      <ObjectiveStatement />
    </div>
  )
}
