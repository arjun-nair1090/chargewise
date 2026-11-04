/**
 * The ChargeWise mark: three ledger bars of falling length, the outcome bar turned on its side. The top bar is the
 * assigned share, so it carries the brand lime; the others are the waitlisted and rejected remainder.
 */
export function BrandMark({ className = 'size-6' }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={className}>
      <rect x="2" y="3" width="20" height="18" rx="4" className="fill-surface-raised" />
      <rect x="6" y="7" width="12" height="2.5" rx="1" className="fill-accent" />
      <rect x="6" y="10.75" width="8.5" height="2.5" rx="1" className="fill-text" />
      <rect x="6" y="14.5" width="5" height="2.5" rx="1" className="fill-muted" />
    </svg>
  )
}

/** Charge**Wise** with the lime "Wise" (PRODUCT.md brand commitment). */
export function Brand({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <span className={`inline-flex items-center font-semibold tracking-tight ${size === 'lg' ? 'gap-3 text-3xl' : 'gap-2.5 text-lg'}`}>
      <BrandMark className={size === 'lg' ? 'size-10' : 'size-7'} />
      <span>
        Charge<span className="text-accent">Wise</span>
      </span>
    </span>
  )
}
