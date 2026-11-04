import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { buttonClass, type ButtonSize, type ButtonVariant } from './buttonClass'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  busy?: boolean
}

export function Button({
  variant = 'secondary',
  size = 'md',
  busy = false,
  className = '',
  children,
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${buttonClass(variant, size)} ${className}`}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {busy && <Spinner />}
      {children}
    </button>
  )
}

export function Spinner() {
  return (
    <span
      aria-hidden="true"
      className="inline-block size-3.5 animate-spin rounded-full border-2 border-current border-r-transparent"
    />
  )
}

/** A page's title and the single sentence stating its job (PRODUCT.md principle 4). */
export function PageHeader({ title, job, actions, badges }: {
  title: string
  job: ReactNode
  actions?: ReactNode
  badges?: ReactNode
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4 border-b border-border pb-6">
      <div className="min-w-0 max-w-3xl">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-semibold tracking-[-0.025em] sm:text-[2.25rem] sm:leading-tight">{title}</h1>
          {badges}
        </div>
        <p className="mt-2 text-base text-muted">{job}</p>
      </div>
      {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
    </header>
  )
}

/** A charcoal region divided from its neighbours by rules, not shadows. */
export function Panel({ title, aside, children, className = '', bodyClassName = 'p-5', labelledBy }: {
  title?: ReactNode
  aside?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
  labelledBy?: string
}) {
  return (
    <section aria-labelledby={labelledBy} className={`min-w-0 rounded-lg border border-border bg-surface ${className}`}>
      {title && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-3">
          <h2 id={labelledBy} className="text-sm font-semibold">{title}</h2>
          {aside && <div className="text-sm text-muted">{aside}</div>}
        </div>
      )}
      <div className={bodyClassName}>{children}</div>
    </section>
  )
}

/** A labelled figure in a readout strip. `hero` marks the one number the view exists to show. */
export function Readout({ label, value, unit, note, hero = false }: {
  label: string
  value: ReactNode
  unit?: string
  note?: ReactNode
  hero?: boolean
}) {
  return (
    <div className="flex min-w-0 flex-col px-5 py-5">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="mt-2 flex items-baseline">
        <span className={`font-semibold tabular-nums tracking-[-0.03em] ${hero ? 'text-5xl leading-none text-accent' : 'text-[2rem] leading-none text-text'}`}>
          {value}
        </span>
        {unit && <span className="ml-1.5 font-mono text-sm text-muted">{unit}</span>}
      </dd>
      {note && <dd className="mt-auto pt-2.5 text-xs text-muted">{note}</dd>}
    </div>
  )
}

/** Up to four readouts in one ruled strip: two columns on phones, one row from tablet width. */
export function ReadoutStrip({ children, label }: { children: ReactNode; label: string }) {
  return (
    <dl
      aria-label={label}
      className="grid grid-cols-2 rounded-lg border border-border bg-surface md:grid-cols-4 md:divide-x md:divide-border [&>div:nth-child(n+3)]:border-t [&>div:nth-child(n+3)]:border-border md:[&>div:nth-child(n+3)]:border-t-0"
    >
      {children}
    </dl>
  )
}

/** Identifiers, units and measurements; never decoration. */
export function Mono({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <span className={`font-mono text-[0.92em] tabular-nums ${className}`}>{children}</span>
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="px-5 py-10 text-center">
      <p className="font-medium">{title}</p>
      {children && <div className="mx-auto mt-2 max-w-md text-sm text-muted">{children}</div>}
    </div>
  )
}

/** Shows a form-level server error that is not tied to one field. */
export function FormError({ message }: { message: string | null | undefined }) {
  if (!message) {
    return null
  }
  return (
    <p role="alert" className="rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
      {message}
    </p>
  )
}
