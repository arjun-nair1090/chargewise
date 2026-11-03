import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'

const controlClass =
  'h-10 w-full min-w-0 rounded-md border bg-bg px-3 text-sm text-text transition-colors duration-150 hover:border-border-strong focus-visible:border-accent disabled:opacity-50'

type FieldIds = { id: string; describedBy: string | undefined; invalid: boolean }

function FieldShell({ label, hint, error, children }: {
  label: string
  hint?: ReactNode
  error?: string
  children: (ids: FieldIds) => ReactNode
}) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">{label}</label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {hint && <p id={hintId} className="text-xs text-muted">{hint}</p>}
      {error && <p id={errorId} className="text-xs text-danger">{error}</p>}
    </div>
  )
}

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: ReactNode; error?: string }

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, error, className = '', ...input },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => (
        <input
          ref={ref}
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className={`${controlClass} ${invalid ? 'border-danger' : 'border-border'} ${className}`}
          {...input}
        />
      )}
    </FieldShell>
  )
})

type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string
  hint?: ReactNode
  error?: string
  children: ReactNode
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, hint, error, className = '', children, ...select },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => (
        <select
          ref={ref}
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className={`${controlClass} ${invalid ? 'border-danger' : 'border-border'} ${className}`}
          {...select}
        >
          {children}
        </select>
      )}
    </FieldShell>
  )
})
