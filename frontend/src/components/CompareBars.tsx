/**
 * Algorithm (series slot 1) against baseline (slot 2) on one measure. Both bars are direct-labelled with their
 * name and value, so identity never rests on colour alone.
 */
export function CompareBars({ title, algorithm, baseline, format = (value) => String(value), higherIsBetter = true }: {
  title: string
  algorithm: { name: string; value: number }
  baseline: { name: string; value: number }
  format?: (value: number) => string
  higherIsBetter?: boolean
}) {
  const max = Math.max(algorithm.value, baseline.value, Number.EPSILON)
  const rows = [
    { ...algorithm, className: 'bg-series-1', role: 'Algorithm' },
    { ...baseline, className: 'bg-series-2', role: 'Baseline' },
  ]
  const difference = algorithm.value - baseline.value
  const verdict = difference === 0
    ? 'Same result on this input.'
    : (difference > 0) === higherIsBetter
      ? `Algorithm ahead by ${format(Math.abs(difference))}.`
      : `Baseline ahead by ${format(Math.abs(difference))}.`
  return (
    <figure className="flex flex-col gap-2">
      <figcaption className="flex flex-wrap items-baseline justify-between gap-x-4 text-sm">
        <span className="font-medium">{title}</span>
        <span className="text-muted">{verdict}</span>
      </figcaption>
      {rows.map((row) => (
        <div key={row.role} className="grid grid-cols-[minmax(8rem,12rem)_1fr_auto] items-center gap-3 text-sm">
          <span className="truncate text-muted" title={row.name}>{row.role}: {row.name}</span>
          <span aria-hidden="true" className="h-3 rounded-sm bg-surface-raised">
            <span className={`block h-full rounded-r-[4px] ${row.className}`} style={{ width: `${(row.value / max) * 100}%` }} />
          </span>
          <span className="tabular-nums">{format(row.value)}</span>
        </div>
      ))}
    </figure>
  )
}
