import { humanise } from '../lib/format'
import { statusTone } from '../lib/statusTone'

/** A status is a small square plus a word, never colour alone. */
export function StatusChip({ status }: { status: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm whitespace-nowrap">
      <span aria-hidden="true" className={`inline-block size-2 ${statusTone(status)}`} />
      {humanise(status)}
    </span>
  )
}

const BADGE_TEXT = {
  simulated: { label: 'Simulated', title: 'Charging sessions are simulated with a constant-power model.' },
  generated: { label: 'Generated inputs', title: 'Every input is generated from the run seed, not taken from real stations or drivers.' },
  measured: { label: 'Measured', title: 'Every time shown was measured on this server; nothing is estimated.' },
}

/** Honest-data label (PRODUCT.md principle 2). */
export function DataBadge({ kind }: { kind: keyof typeof BADGE_TEXT }) {
  const text = BADGE_TEXT[kind]
  return (
    <span
      title={text.title}
      className="inline-flex h-6 items-center rounded border border-border-strong px-2 text-xs font-medium text-muted"
    >
      {text.label}
    </span>
  )
}
