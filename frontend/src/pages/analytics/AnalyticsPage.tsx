import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { QueryState } from '../../components/QueryState'
import { SlotBarChart } from '../../components/SlotBarChart'
import { DataBadge } from '../../components/StatusChip'
import { Button, FormError, PageHeader, Panel } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { formatInteger, formatNs, formatTime } from '../../lib/format'
import { floorToSlot, SLOT_MS, toIsoSeconds } from '../../lib/slots'
import { labApi, operatorApi, stationApi } from '../../services/endpoints'
import type { AvailabilityResponse } from '../../types/api'
import { useStations } from '../operator/useStationNames'
import { PercentileLedger } from './PercentileLedger'

const HORIZON_SLOTS = 96

/** Booked connectors per slot: operational connectors minus the free count the backend reports. */
function occupiedSeries(availability: AvailabilityResponse): number[] {
  return availability.slots.map((slot) => Math.max(0, availability.operationalConnectors - slot.freeConnectors))
}

function Percentiles() {
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: operatorApi.dashboard })
  const runId = dashboard.data?.latestAllocationRunId ?? null
  const run = useQuery({
    queryKey: ['allocation', runId],
    queryFn: () => operatorApi.allocation(runId!),
    enabled: runId !== null,
  })
  return (
    <QueryState query={dashboard} rows={2}>
      {(data) => <PercentileLedger waiting={data.waiting} power={run.data?.result.requestedPower} runId={runId} />}
    </QueryState>
  )
}

function RangeExplorer({ availability, stationName }: { availability: AvailabilityResponse; stationName: string }) {
  const labels = availability.slots.map((slot) => formatTime(slot.start))
  const [from, setFrom] = useState(0)
  const [to, setTo] = useState(Math.min(15, labels.length - 1))
  const query = useMutation({
    mutationFn: () =>
      labApi.segmentTree({
        values: occupiedSeries(availability),
        operations: [{ type: 'MAX', from, to }, { type: 'SUM', from, to }],
      }),
  })
  const max = query.data?.results.find((row) => row.type === 'MAX')
  const sum = query.data?.results.find((row) => row.type === 'SUM')
  const select = 'h-10 rounded-md border border-border bg-bg px-3 text-sm text-text'
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">From slot</span>
          <select className={select} value={from} onChange={(event) => { const value = Number(event.target.value); setFrom(value); setTo(Math.max(to, value)); query.reset() }}>
            {labels.map((label, index) => <option key={index} value={index}>{label}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">To slot (inclusive)</span>
          <select className={select} value={to} onChange={(event) => { setTo(Number(event.target.value)); query.reset() }}>
            {labels.map((label, index) => index >= from && <option key={index} value={index}>{label}</option>)}
          </select>
        </label>
        <Button variant="primary" busy={query.isPending} onClick={() => query.mutate()}>Query range</Button>
      </div>
      {query.isError && <FormError message={errorMessage(query.error)} />}
      {query.data && max && sum && (
        <dl className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-muted">Peak booked connectors</dt>
            <dd className="text-3xl font-semibold tabular-nums">{formatInteger(max.tree)}</dd>
            <dd className="text-xs text-muted">Free at the busiest slot: {formatInteger(availability.operationalConnectors - max.tree)}</dd>
          </div>
          <div>
            <dt className="text-muted">Booked connector-slots</dt>
            <dd className="text-3xl font-semibold tabular-nums">{formatInteger(sum.tree)}</dd>
            <dd className="text-xs text-muted">{formatInteger(sum.tree * 15)} connector-minutes</dd>
          </div>
          <div>
            <dt className="text-muted">Check against a naive scan</dt>
            <dd className="mt-1">{query.data.agree ? 'Both answers agree.' : 'The answers differ.'}</dd>
            <dd className="text-xs text-muted">Segment tree {formatNs(query.data.algorithm.runtimeNs)}, naive {formatNs(query.data.baseline.runtimeNs)}</dd>
          </div>
        </dl>
      )}
      <p className="text-xs text-muted">
        The booked-per-slot series for {stationName} is sent to the server&rsquo;s segment tree, which answers MAX and SUM over
        slots {labels[from]}–{labels[to]}.
      </p>
    </div>
  )
}

export function AnalyticsPage() {
  const stations = useStations()
  const [stationId, setStationId] = useState<number | null>(null)
  const [from] = useState(() => floorToSlot(new Date()))
  const selected = stationId ?? stations.data?.content[0]?.id ?? null
  const fromIso = toIsoSeconds(from)
  const toIso = toIsoSeconds(new Date(from.getTime() + HORIZON_SLOTS * SLOT_MS))
  const availability = useQuery({
    queryKey: ['availability', selected, fromIso, toIso],
    queryFn: () => stationApi.availability(selected!, fromIso, toIso),
    enabled: selected !== null,
  })
  const stationName = stations.data?.content.find((station) => station.id === selected)?.name ?? ''

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Analytics"
        job="Waiting and demand percentiles, and how booked each station is over the next 24 hours."
        badges={<DataBadge kind="synthetic" />}
      />
      <Percentiles />

      <Panel
        title="Congestion: booked connectors per 15-minute slot, next 24 hours"
        aside={
          <label className="inline-flex items-center gap-2">
            <span>Station</span>
            <select
              value={selected ?? ''}
              onChange={(event) => setStationId(Number(event.target.value))}
              className="h-8 max-w-full rounded-md border border-border bg-bg px-2 text-sm text-text"
            >
              {stations.data?.content.map((station) => <option key={station.id} value={station.id}>{station.name}</option>)}
            </select>
          </label>
        }
      >
        <QueryState query={availability} rows={5}>
          {(data) => (
            <div className="flex flex-col gap-6">
              <SlotBarChart
                bars={data.slots.map((slot, index) => ({
                  label: formatTime(slot.start),
                  value: occupiedSeries(data)[index]!,
                  detail: [`${slot.freeConnectors} of ${data.operationalConnectors} free`],
                }))}
                unit="booked"
                title={`Booked connectors per slot at ${stationName}`}
                maxValue={Math.max(1, data.operationalConnectors)}
              />
              <div className="border-t border-border pt-5">
                <h3 className="text-sm font-semibold">Range explorer</h3>
                <p className="mt-1 mb-4 text-sm text-muted">Pick a slot range; the server&rsquo;s segment tree reports the peak and the total.</p>
                <RangeExplorer key={`${selected}-${fromIso}`} availability={data} stationName={stationName} />
              </div>
            </div>
          )}
        </QueryState>
      </Panel>
    </div>
  )
}
