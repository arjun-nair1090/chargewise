/** Swatch colour per status. Status is always a swatch plus a word, never colour alone. */
const TONE: Record<string, string> = {
  ASSIGNED: 'bg-accent',
  ACTIVE: 'bg-accent',
  OPERATIONAL: 'bg-accent',
  WAITLISTED: 'bg-warning',
  SCHEDULED: 'bg-warning',
  MAINTENANCE: 'bg-warning',
  PENDING: 'bg-muted',
  RUNNING: 'bg-warning',
  FAILED: 'bg-danger',
  REJECTED: 'bg-danger',
  INTERRUPTED: 'bg-danger',
  OFFLINE: 'bg-danger',
  OUT_OF_SERVICE: 'bg-danger',
  CANCELLED: 'bg-border-strong',
  COMPLETED: 'bg-text',
}

/** The status's swatch class (shared by chips and the split bars that summarise them). */
export function statusTone(status: string): string {
  return TONE[status] ?? 'bg-muted'
}
