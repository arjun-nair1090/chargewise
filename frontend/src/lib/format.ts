/** Display formatting only. Every number shown comes from the backend; nothing here computes a result. */

const twoDecimals = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 })
const integer = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })

export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : twoDecimals.format(value)
}

export function formatInteger(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : integer.format(value)
}

export function formatKw(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${twoDecimals.format(value)} kW`
}

export function formatKwh(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${twoDecimals.format(value)} kWh`
}

/** A 0–1 ratio as a whole percentage. */
export function formatRatio(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${Math.round(value * 100)}%`
}

/** Nanoseconds as milliseconds, enough precision to compare algorithm and baseline. */
export function formatNs(ns: number | null | undefined): string {
  if (ns === null || ns === undefined) {
    return '—'
  }
  const ms = ns / 1_000_000
  return `${ms < 10 ? ms.toFixed(3) : ms.toFixed(1)} ms`
}

const NANO_UNITS = [
  { unit: 'ns', scale: 1 },
  { unit: 'µs', scale: 1_000 },
  { unit: 'ms', scale: 1_000_000 },
  { unit: 's', scale: 1_000_000_000 },
] as const

/** Nanoseconds in the largest unit that keeps the value under 1,000, to three significant figures. */
export function formatNanos(ns: number | null | undefined): string {
  if (ns === null || ns === undefined) {
    return '—'
  }
  // A unit is used once the value would round to 1,000 or more of the unit below it.
  const { unit, scale } = NANO_UNITS.findLast((candidate) => Math.abs(ns) >= candidate.scale * 0.9995) ?? NANO_UNITS[0]
  const value = ns / scale
  const whole = unit === 'ns' || (unit === 's' && Math.abs(value) >= 100)
  const text = whole ? String(Math.round(value)) : value.toPrecision(3)
  return `${text} ${unit}`
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) {
    return '—'
  }
  if (seconds < 60) {
    return `${Math.round(seconds)} s`
  }
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) {
    return `${minutes} min`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours < 48) {
    return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
  }
  return `${Math.round(hours / 24)} d`
}

const dateTime = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
})
const timeOnly = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' })

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : '—'
}

export function formatTime(iso: string): string {
  return timeOnly.format(new Date(iso))
}

/** A half-open booking window `[start, end)` in local time, e.g. "Tue 3 Nov, 10:00–11:00". */
export function formatWindow(start: string, end: string): string {
  const startDate = new Date(start)
  const endDate = new Date(end)
  const sameDay = startDate.toDateString() === endDate.toDateString()
  return sameDay ? `${dateTime.format(startDate)}–${timeOnly.format(endDate)}` : `${dateTime.format(startDate)} – ${dateTime.format(endDate)}`
}

/** Algorithm names keep their authors' spelling; sentence-casing would print "Edmonds karp". */
const PROPER_NAMES: Record<string, string> = {
  EDMONDS_KARP: 'Edmonds-Karp',
  RANDOMIZED_QUICKSELECT: 'Randomized Quickselect',
}

export function humanise(code: string): string {
  if (PROPER_NAMES[code]) {
    return PROPER_NAMES[code]
  }
  const words = code.toLowerCase().split('_')
  return words.map((word, index) => (index === 0 ? word.charAt(0).toUpperCase() + word.slice(1) : word)).join(' ')
}

const CONNECTOR_LABELS: Record<string, string> = {
  CCS2: 'CCS2',
  TYPE2_AC: 'Type 2 AC',
  CHADEMO: 'CHAdeMO',
  BHARAT_DC001: 'Bharat DC-001',
  BHARAT_AC001: 'Bharat AC-001',
}

export function connectorLabel(type: string | null | undefined): string {
  return type ? (CONNECTOR_LABELS[type] ?? type) : '—'
}
