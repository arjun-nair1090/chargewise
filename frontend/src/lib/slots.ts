/** 15-minute slot helpers (architecture D2). Windows are half-open `[start, end)`. */

export const SLOT_MINUTES = 15
export const SLOT_MS = SLOT_MINUTES * 60_000
export const MAX_WINDOW_SLOTS = 48
export const MAX_DAYS_AHEAD = 14

/** "00:00", "00:15", … "23:45": the 96 start times a booking can use. */
export const SLOT_TIMES: string[] = Array.from({ length: 96 }, (_, index) => {
  const minutes = index * SLOT_MINUTES
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
})

export function floorToSlot(date: Date): Date {
  return new Date(Math.floor(date.getTime() / SLOT_MS) * SLOT_MS)
}

export function ceilToSlot(date: Date): Date {
  return new Date(Math.ceil(date.getTime() / SLOT_MS) * SLOT_MS)
}

/** ISO-8601 UTC without milliseconds, e.g. "2026-11-03T04:45:00Z". */
export function toIsoSeconds(date: Date): string {
  return date.toISOString().replace(/\.\d{3}Z$/, 'Z')
}

/** "YYYY-MM-DD" in local time, the value format of `<input type="date">`. */
export function localDateValue(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function localTimeValue(date: Date): string {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

/** Local date "YYYY-MM-DD" plus local time "HH:MM" as a Date; null when either part is malformed. */
export function combineLocal(dateValue: string, timeValue: string): Date | null {
  const dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateValue)
  const timeMatch = /^(\d{2}):(\d{2})$/.exec(timeValue)
  if (!dateMatch || !timeMatch) {
    return null
  }
  const date = new Date(
    Number(dateMatch[1]),
    Number(dateMatch[2]) - 1,
    Number(dateMatch[3]),
    Number(timeMatch[1]),
    Number(timeMatch[2]),
  )
  return Number.isNaN(date.getTime()) ? null : date
}

export function slotsLabel(slots: number): string {
  const minutes = slots * SLOT_MINUTES
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) {
    return `${rest} min`
  }
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}
