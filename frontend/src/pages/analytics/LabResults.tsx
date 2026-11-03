import { CompareBars } from '../../components/CompareBars'
import { Mono } from '../../components/ui'
import { formatInteger, formatKw, formatNs, humanise } from '../../lib/format'
import type {
  MatchingLabResponse,
  MaxFlowLabResponse,
  PowerRun,
  QuickselectLabResponse,
  SegmentTreeLabResponse,
} from '../../types/api'

function Verdict({ ok, yes, no }: { ok: boolean; yes: string; no: string }) {
  return (
    <p className={`text-sm font-medium ${ok ? 'text-text' : 'text-danger'}`}>
      <span aria-hidden="true" className={`mr-2 inline-block size-2 ${ok ? 'bg-accent' : 'bg-danger'}`} />
      {ok ? yes : no}
    </p>
  )
}

function WorkTable({ caption, rows }: { caption: string; rows: { label: string; algorithm: string; baseline: string }[] }) {
  return (
    <table className="data-table">
      <caption className="sr-only">{caption}</caption>
      <thead><tr><th scope="col">Measure</th><th scope="col" className="text-right">Algorithm</th><th scope="col" className="text-right">Baseline</th></tr></thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.label}><td>{row.label}</td><td className="text-right">{row.algorithm}</td><td className="text-right">{row.baseline}</td></tr>
        ))}
      </tbody>
    </table>
  )
}

export function MatchingResult({ result }: { result: MatchingLabResponse }) {
  const { algorithm, baseline } = result
  return (
    <div className="flex flex-col gap-5">
      <Verdict ok={algorithm.valid} yes="The matching passed the independent validity check." no="The matching failed the validity check." />
      <CompareBars
        title="Assignments made"
        algorithm={{ name: humanise(algorithm.name), value: algorithm.cardinality }}
        baseline={{ name: humanise(baseline.name), value: baseline.cardinality }}
      />
      <WorkTable
        caption="Matching work"
        rows={[
          { label: 'Edges examined', algorithm: formatInteger(algorithm.edgesExamined), baseline: formatInteger(baseline.edgesExamined) },
          { label: 'Augmenting paths', algorithm: formatInteger(algorithm.augmentations), baseline: formatInteger(baseline.augmentations) },
          { label: 'Reassignments', algorithm: formatInteger(algorithm.reassignments), baseline: formatInteger(baseline.reassignments) },
          { label: 'Runtime', algorithm: formatNs(algorithm.runtimeNs), baseline: formatNs(baseline.runtimeNs) },
        ]}
      />
      <p className="text-sm text-muted">
        Unmatched by the algorithm: {algorithm.unmatched.length ? <Mono>{algorithm.unmatched.join(', ')}</Mono> : 'none'}.
        Unmatched by greedy: {baseline.unmatched.length ? <Mono>{baseline.unmatched.join(', ')}</Mono> : 'none'}.
      </p>
      <details className="text-sm">
        <summary className="cursor-pointer text-muted hover:text-text">Show the {algorithm.pairs.length} matched pairs</summary>
        <p className="mt-2"><Mono>{algorithm.pairs.map((pair) => `${pair.request}→${pair.connector}`).join('  ')}</Mono></p>
      </details>
    </div>
  )
}

function PowerComparison({ algorithm, baseline }: { algorithm: PowerRun; baseline: PowerRun }) {
  return (
    <div className="flex flex-col gap-5">
      <Verdict
        ok={algorithm.violations.length === 0}
        yes="No capacity constraint is violated by the Edmonds-Karp allocation."
        no={`Violations: ${algorithm.violations.join('; ')}`}
      />
      <CompareBars
        title="Power allocated"
        algorithm={{ name: humanise(algorithm.name), value: algorithm.totalAllocatedKw }}
        baseline={{ name: humanise(baseline.name), value: baseline.totalAllocatedKw }}
        format={formatKw}
      />
      <WorkTable
        caption="Power allocation work"
        rows={[
          { label: 'Requested', algorithm: formatKw(algorithm.totalRequestedKw), baseline: formatKw(baseline.totalRequestedKw) },
          { label: 'Unserved', algorithm: formatKw(algorithm.unservedKw), baseline: formatKw(baseline.unservedKw) },
          { label: 'Operations', algorithm: formatInteger(algorithm.operations), baseline: formatInteger(baseline.operations) },
          { label: 'Runtime', algorithm: formatNs(algorithm.runtimeNs), baseline: formatNs(baseline.runtimeNs) },
        ]}
      />
      <div className="text-sm">
        <p className="font-medium">Bottlenecks (Edmonds-Karp)</p>
        <ul className="mt-1 text-muted">
          {algorithm.bottlenecks.map((item) => (
            <li key={`${item.kind}-${item.id}`}>{humanise(item.kind)} <Mono>{item.id}</Mono> at {formatKw(item.capacityKw)}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}

export function MaxFlowResult({ result }: { result: MaxFlowLabResponse }) {
  if (result.mode === 'POWER' && result.power) {
    return <PowerComparison algorithm={result.power.algorithm} baseline={result.power.baseline} />
  }
  const network = result.network!
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end gap-x-10 gap-y-3">
        <div>
          <p className="text-sm text-muted">Maximum flow</p>
          <p className="text-4xl font-semibold tabular-nums text-accent">{formatInteger(network.totalFlow)}</p>
        </div>
        <div>
          <p className="text-sm text-muted">Minimum cut capacity</p>
          <p className="text-3xl font-semibold tabular-nums">{formatInteger(network.minCutCapacity)}</p>
        </div>
      </div>
      <Verdict
        ok={network.totalFlow === network.minCutCapacity}
        yes="Flow equals the minimum cut, as max-flow min-cut requires."
        no="Flow and minimum cut differ."
      />
      <p className="text-sm text-muted">
        {network.augmentations} augmenting paths, {network.bfsRuns} breadth-first searches, {formatInteger(network.edgesScanned)} edges
        scanned, {formatNs(network.runtimeNs)}.
      </p>
      <table className="data-table">
        <caption className="px-5 py-2 text-left text-sm font-medium">Minimum cut: the saturated edges that bound the flow</caption>
        <thead><tr><th scope="col">From</th><th scope="col">To</th><th scope="col" className="text-right">Capacity</th></tr></thead>
        <tbody>
          {network.minCut.map((edge) => (
            <tr key={`${edge.from}-${edge.to}`}><td><Mono>{edge.from}</Mono></td><td><Mono>{edge.to}</Mono></td><td className="text-right">{edge.capacity}</td></tr>
          ))}
        </tbody>
      </table>
      <details className="text-sm">
        <summary className="cursor-pointer text-muted hover:text-text">Show flow on every edge</summary>
        <table className="data-table mt-2">
          <thead><tr><th scope="col">From</th><th scope="col">To</th><th scope="col" className="text-right">Flow / capacity</th></tr></thead>
          <tbody>
            {network.edges.map((edge) => (
              <tr key={`${edge.from}-${edge.to}`}><td><Mono>{edge.from}</Mono></td><td><Mono>{edge.to}</Mono></td><td className="text-right">{edge.flow} / {edge.capacity}</td></tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}

export function QuickselectResult({ result }: { result: QuickselectLabResponse }) {
  return (
    <div className="flex flex-col gap-5">
      <Verdict ok={result.agree} yes={`Quickselect agrees with a full sort on all ${result.selections.length} selections.`} no="Quickselect and the full sort disagree." />
      <table className="data-table">
        <caption className="sr-only">Selections</caption>
        <thead><tr><th scope="col">Selection</th><th scope="col" className="text-right">k-th smallest</th><th scope="col" className="text-right">Quickselect</th><th scope="col" className="text-right">Full sort</th></tr></thead>
        <tbody>
          {result.selections.map((selection) => (
            <tr key={selection.label}>
              <td>{selection.label}</td>
              <td className="text-right">{formatInteger(selection.rank + 1)}</td>
              <td className="text-right">{formatInteger(selection.value)}</td>
              <td className="text-right">{formatInteger(selection.baselineValue)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <CompareBars
        title="Runtime"
        algorithm={{ name: humanise(result.algorithm.name), value: result.algorithm.runtimeNs }}
        baseline={{ name: humanise(result.baseline.name), value: result.baseline.runtimeNs }}
        format={formatNs}
        higherIsBetter={false}
      />
      <p className="text-sm text-muted">
        n = {formatInteger(result.n)}, seed {result.seed}. Quickselect: {formatInteger(result.algorithm.comparisons)} comparisons,{' '}
        {formatInteger(result.algorithm.swaps)} swaps, {result.algorithm.partitionRounds} partition rounds. The full sort is not
        instrumented for comparisons.
      </p>
    </div>
  )
}

export function SegmentTreeResult({ result }: { result: SegmentTreeLabResponse }) {
  return (
    <div className="flex flex-col gap-5">
      <Verdict ok={result.agree} yes="The segment tree agrees with a naive scan on every query." no="The segment tree and the naive scan disagree." />
      <CompareBars
        title={`Runtime for ${result.operationCount} operations on ${formatInteger(result.n)} values`}
        algorithm={{ name: humanise(result.algorithm.name), value: result.algorithm.runtimeNs }}
        baseline={{ name: humanise(result.baseline.name), value: result.baseline.runtimeNs }}
        format={formatNs}
        higherIsBetter={false}
      />
      <div className="max-h-80 overflow-auto">
        <table className="data-table">
          <caption className="sr-only">Query results</caption>
          <thead><tr><th scope="col">Operation</th><th scope="col">Query</th><th scope="col" className="text-right">Segment tree</th><th scope="col" className="text-right">Naive scan</th></tr></thead>
          <tbody>
            {result.results.map((row) => (
              <tr key={row.operation}>
                <td><Mono>#{row.operation}</Mono></td>
                <td>{row.type} [{row.from}, {row.to}]</td>
                <td className="text-right">{formatInteger(row.tree)}</td>
                <td className="text-right">{formatInteger(row.naive)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
