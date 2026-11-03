import { useMutation } from '@tanstack/react-query'
import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { ApiErrorAlert } from '../../components/ApiErrorAlert'
import { ObjectiveStatement } from '../../components/ObjectiveStatement'
import { Button, Mono, PageHeader, Panel } from '../../components/ui'
import { generateInstance, LAB_ALGORITHMS, LAB_EXAMPLES, type LabAlgorithm } from '../../lib/labInstances'
import { labApi } from '../../services/endpoints'
import type {
  MatchingLabRequest,
  MatchingLabResponse,
  MaxFlowLabRequest,
  MaxFlowLabResponse,
  QuickselectLabRequest,
  QuickselectLabResponse,
  SegmentTreeLabRequest,
  SegmentTreeLabResponse,
} from '../../types/api'
import { MatchingResult, MaxFlowResult, QuickselectResult, SegmentTreeResult } from './LabResults'

const TABS: Record<LabAlgorithm, { label: string; job: string; shape: string }> = {
  matching: {
    label: 'Bipartite matching',
    job: 'Maximum matching of requests to compatible connectors, against greedy first-available.',
    shape: '{ requests: [ids], connectors: [ids], edges: [{ request, connector }] }',
  },
  'max-flow': {
    label: 'Edmonds-Karp',
    job: 'Maximum flow on a network, or charging power against the greedy allocator when given a power network.',
    shape: '{ network: { nodes, edges: [{ from, to, capacity }], source, sink } }  or  { power: { stations, sessions, feeders?, links? } }',
  },
  quickselect: {
    label: 'Randomized Quickselect',
    job: 'Order statistics in expected linear time, checked against a full sort.',
    shape: '{ values: [integers], percentiles: [1–100] | k: 1-based rank, seed? }',
  },
  'segment-tree': {
    label: 'Segment tree',
    job: 'Range SUM and MAX with point updates in logarithmic time, checked against a naive scan.',
    shape: '{ values: [integers], operations: [{ type: SUM|MAX, from, to } | { type: SET|ADD, index, value }] }',
  },
}

type LabResult =
  | { algorithm: 'matching'; data: MatchingLabResponse }
  | { algorithm: 'max-flow'; data: MaxFlowLabResponse }
  | { algorithm: 'quickselect'; data: QuickselectLabResponse }
  | { algorithm: 'segment-tree'; data: SegmentTreeLabResponse }

async function execute(algorithm: LabAlgorithm, input: unknown): Promise<LabResult> {
  switch (algorithm) {
    case 'matching':
      return { algorithm, data: await labApi.matching(input as MatchingLabRequest) }
    case 'max-flow':
      return { algorithm, data: await labApi.maxFlow(input as MaxFlowLabRequest) }
    case 'quickselect':
      return { algorithm, data: await labApi.quickselect(input as QuickselectLabRequest) }
    case 'segment-tree':
      return { algorithm, data: await labApi.segmentTree(input as SegmentTreeLabRequest) }
  }
}

const pretty = (value: unknown) => JSON.stringify(value, null, 2)

function LabErrors({ error, parseError }: { error: unknown; parseError: string | null }) {
  if (parseError) {
    return <div role="alert" className="rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">{parseError}</div>
  }
  return <ApiErrorAlert error={error} />
}

export function AlgorithmLabPage() {
  const [algorithm, setAlgorithm] = useState<LabAlgorithm>('matching')
  const [inputs, setInputs] = useState<Record<LabAlgorithm, string>>(() => ({
    matching: pretty(LAB_EXAMPLES.matching[0]!.input),
    'max-flow': pretty(LAB_EXAMPLES['max-flow'][0]!.input),
    quickselect: pretty(LAB_EXAMPLES.quickselect[0]!.input),
    'segment-tree': pretty(LAB_EXAMPLES['segment-tree'][0]!.input),
  }))
  const [seed, setSeed] = useState('20261102')
  const [parseError, setParseError] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const tabRefs = useRef<Partial<Record<LabAlgorithm, HTMLButtonElement | null>>>({})
  const baseId = useId()
  const run = useMutation({ mutationFn: ({ target, input }: { target: LabAlgorithm; input: unknown }) => execute(target, input) })
  const tab = TABS[algorithm]

  function setInput(value: string) {
    setInputs((current) => ({ ...current, [algorithm]: value }))
    setParseError(null)
  }

  function select(next: LabAlgorithm) {
    setAlgorithm(next)
    setParseError(null)
    run.reset()
  }

  function onTabKey(event: KeyboardEvent<HTMLButtonElement>) {
    const index = LAB_ALGORITHMS.indexOf(algorithm)
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (step === 0) {
      return
    }
    event.preventDefault()
    const next = LAB_ALGORITHMS[(index + step + LAB_ALGORITHMS.length) % LAB_ALGORITHMS.length]!
    select(next)
    tabRefs.current[next]?.focus()
  }

  function submit() {
    let parsed: unknown
    try {
      parsed = JSON.parse(inputs[algorithm])
    } catch (error) {
      setParseError(`The input is not valid JSON: ${error instanceof Error ? error.message : 'parse error'}. Nothing was sent.`)
      return
    }
    setParseError(null)
    run.mutate({ target: algorithm, input: parsed })
  }

  async function upload(file: File | undefined) {
    if (file) {
      setInput(await file.text())
    }
  }

  const seedValue = Number.parseInt(seed, 10)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Algorithm Lab"
        job="Give an algorithm an input, run it against its baseline on the server, and read what it did."
      />

      <div role="tablist" aria-label="Algorithm" className="flex flex-wrap gap-1 border-b border-border">
        {LAB_ALGORITHMS.map((key) => (
          <button
            key={key}
            ref={(element) => { tabRefs.current[key] = element }}
            type="button"
            role="tab"
            id={`${baseId}-tab-${key}`}
            aria-selected={algorithm === key}
            aria-controls={`${baseId}-panel`}
            tabIndex={algorithm === key ? 0 : -1}
            onClick={() => select(key)}
            onKeyDown={onTabKey}
            className={`-mb-px h-10 border-b-2 px-3 text-sm transition-colors duration-150 ${
              algorithm === key ? 'border-text font-medium text-text' : 'border-transparent text-muted hover:text-text'
            }`}
          >
            {TABS[key].label}
          </button>
        ))}
      </div>

      <div id={`${baseId}-panel`} role="tabpanel" aria-labelledby={`${baseId}-tab-${algorithm}`} className="grid gap-6 xl:grid-cols-2">
        <Panel title="1. Input and configure" aside={tab.job} bodyClassName="flex flex-col gap-4 p-5">
          <div className="flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="font-medium">Worked example</span>
              <select
                className="h-10 rounded-md border border-border bg-bg px-3 text-sm text-text"
                onChange={(event) => {
                  const example = LAB_EXAMPLES[algorithm][Number(event.target.value)]
                  if (example) {
                    setInput(pretty(example.input))
                  }
                }}
                defaultValue=""
                key={algorithm}
              >
                <option value="" disabled>Choose an example</option>
                {LAB_EXAMPLES[algorithm].map((example, index) => <option key={example.label} value={index}>{example.label}</option>)}
              </select>
            </label>
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="font-medium">Seed</span>
              <input
                value={seed}
                onChange={(event) => setSeed(event.target.value)}
                inputMode="numeric"
                className="h-10 w-32 rounded-md border border-border bg-bg px-3 text-sm tabular-nums text-text"
              />
            </label>
            <Button disabled={!Number.isFinite(seedValue)} onClick={() => setInput(pretty(generateInstance(algorithm, seedValue)))}>
              Generate random instance
            </Button>
            <Button variant="ghost" onClick={() => fileInput.current?.click()}>Upload JSON</Button>
            <input
              ref={fileInput}
              type="file"
              accept="application/json,.json"
              className="sr-only"
              tabIndex={-1}
              aria-hidden="true"
              onChange={(event) => void upload(event.target.files?.[0])}
            />
          </div>
          <div className="flex flex-col gap-1.5 text-sm">
            <label htmlFor={`${baseId}-input`} className="font-medium">Input JSON</label>
            <textarea
              id={`${baseId}-input`}
              value={inputs[algorithm]}
              onChange={(event) => setInput(event.target.value)}
              spellCheck={false}
              rows={16}
              aria-describedby={`${baseId}-shape`}
              className="min-h-64 rounded-md border border-border bg-bg p-3 font-mono text-xs leading-relaxed text-text hover:border-border-strong focus-visible:border-accent"
            />
            <span id={`${baseId}-shape`} className="text-xs text-muted">Shape: <Mono>{tab.shape}</Mono></span>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="primary" busy={run.isPending} onClick={submit}>Execute</Button>
            <span className="text-xs text-muted">Caps: 5,000 vertices, 50,000 edges, 1,000,000 values.</span>
          </div>
          <LabErrors error={run.error} parseError={parseError} />
        </Panel>

        <Panel title="2. Interpret" aside={run.data ? 'Server result' : undefined}>
          {run.data && run.data.algorithm === algorithm ? (
            <>
              {run.data.algorithm === 'matching' && <MatchingResult result={run.data.data} />}
              {run.data.algorithm === 'max-flow' && <MaxFlowResult result={run.data.data} />}
              {run.data.algorithm === 'quickselect' && <QuickselectResult result={run.data.data} />}
              {run.data.algorithm === 'segment-tree' && <SegmentTreeResult result={run.data.data} />}
            </>
          ) : (
            <p className="text-sm text-muted">
              Execute the input to see the algorithm&rsquo;s result, its operation counts and runtime, and the baseline on the
              same input.
            </p>
          )}
        </Panel>
      </div>
      {(algorithm === 'matching' || algorithm === 'max-flow') && <ObjectiveStatement />}
    </div>
  )
}
