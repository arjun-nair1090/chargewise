import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export type SlotBar = {
  /** Slot start label, e.g. "10:15". */
  label: string
  value: number
  /** Extra tooltip lines, e.g. free connectors by type. */
  detail?: string[]
}

const AXIS = { fill: '#8b9690', fontSize: 12 }

function SlotTooltip({ active, payload, unit }: { active?: boolean; payload?: { payload: SlotBar }[]; unit: string }) {
  const bar = payload?.[0]?.payload
  if (!active || !bar) {
    return null
  }
  return (
    <div className="rounded-md border border-border-strong bg-surface-raised px-3 py-2 text-xs shadow-[0_4px_16px_rgb(0_0_0/0.4)]">
      <p className="font-medium text-text">{bar.label}</p>
      <p className="tabular-nums text-text">{bar.value} {unit}</p>
      {bar.detail?.map((line) => <p key={line} className="text-muted">{line}</p>)}
    </div>
  )
}

/**
 * One series of per-slot magnitudes from the backend (slot 1 colour, single hue). The same rows are available
 * as a table for screen readers and for anyone who prefers numbers.
 */
export function SlotBarChart({ bars, unit, title, maxValue, height = 200 }: {
  bars: SlotBar[]
  unit: string
  title: string
  maxValue?: number
  height?: number
}) {
  return (
    <figure className="min-w-0">
      <div aria-hidden="true" style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={bars} margin={{ top: 8, right: 8, bottom: 0, left: -16 }} barCategoryGap={2}>
            <CartesianGrid vertical={false} stroke="#2a302c" />
            <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={{ stroke: '#3a423d' }} interval="preserveStartEnd" minTickGap={20} />
            <YAxis tick={AXIS} tickLine={false} axisLine={false} allowDecimals={false} domain={[0, maxValue ?? 'auto']} />
            <Tooltip cursor={{ fill: 'rgb(242 245 243 / 0.06)' }} content={<SlotTooltip unit={unit} />} />
            <Bar dataKey="value" fill="var(--color-series-1)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="sr-only">{title}</figcaption>
      <details className="mt-3 text-sm">
        <summary className="cursor-pointer text-muted hover:text-text">Show as table</summary>
        <div className="mt-2 max-h-64 overflow-auto">
          <table className="data-table">
            <caption className="sr-only">{title}</caption>
            <thead><tr><th scope="col">Slot</th><th scope="col" className="text-right">{unit}</th></tr></thead>
            <tbody>
              {bars.map((bar) => (
                <tr key={bar.label}><td>{bar.label}</td><td className="text-right tabular-nums">{bar.value}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  )
}
