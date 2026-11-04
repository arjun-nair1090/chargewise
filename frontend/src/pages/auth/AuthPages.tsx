import { zodResolver } from '@hookform/resolvers/zod'
import { useState, type ReactNode } from 'react'
import { useForm, type FieldValues, type Path, type UseFormSetError } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { homeFor, useAuth } from '../../auth/authContext'
import { Brand } from '../../components/Brand'
import { TextField } from '../../components/Field'
import { Button, FormError } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { ApiError } from '../../services/api'
import { authApi } from '../../services/endpoints'

/** What the platform decides, and the method behind each decision. */
const DECISIONS = [
  { capability: 'Connector matching', method: 'Maximum bipartite matching', decides: 'Which request gets which connector' },
  { capability: 'Power sharing', method: 'Edmonds-Karp max flow', decides: 'How much power each session draws within the grid limit' },
  { capability: 'Wait-time percentiles', method: 'Randomized Quickselect', decides: 'Median and p90 waiting time, without sorting the queue' },
  { capability: 'Slot availability', method: 'Segment tree', decides: 'Committed load in every 15-minute slot' },
]

function AuthFrame({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <main className="min-h-dvh px-4 py-10 sm:px-8 lg:grid lg:place-items-center lg:py-16">
      <div className="mx-auto grid w-full max-w-6xl gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,25rem)] lg:items-center lg:gap-20">
        <div className="min-w-0">
          <Brand size="lg" />
          <p className="mt-10 max-w-[42rem] text-balance text-4xl font-semibold leading-[1.05] tracking-[-0.035em] sm:text-5xl lg:text-[3.5rem]">
            Every charging decision, with its reason.
          </p>
          <p className="mt-5 max-w-lg text-base text-muted">
            Connector matching and grid-power allocation for EV charging networks. Every assignment, waitlist and power
            share comes with the limit that shaped it.
          </p>
          <dl aria-label="What ChargeWise decides" className="mt-10 hidden max-w-xl border-t border-border sm:block">
            {DECISIONS.map((row) => (
              <div key={row.capability} className="grid grid-cols-[minmax(0,14rem)_minmax(0,1fr)] gap-x-6 border-b border-border py-3.5 text-sm">
                <dt className="flex gap-2.5">
                  <span aria-hidden="true" className="mt-[0.4rem] inline-block size-2 shrink-0 bg-text" />
                  <span>
                    <span className="block font-medium">{row.capability}</span>
                    <span className="mt-0.5 block text-xs text-muted">{row.method}</span>
                  </span>
                </dt>
                <dd className="text-muted">{row.decides}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="w-full min-w-0">
          <section className="rounded-lg border border-border bg-surface p-6 sm:p-8">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            <div className="mt-6">{children}</div>
          </section>
          <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
            {footer}
          </div>
        </div>
      </div>
    </main>
  )
}

function applyServerErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>, fields: string[]): string {
  if (error instanceof ApiError) {
    for (const fieldError of error.fieldErrors) {
      if (fields.includes(fieldError.field)) {
        setError(fieldError.field as Path<T>, { type: 'server', message: fieldError.message })
      }
    }
  }
  return errorMessage(error)
}

const signInSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email.').email('Enter a valid email address.'),
  password: z.string().min(1, 'Enter your password.'),
})
type SignInValues = z.infer<typeof signInSchema>

export function SignInPage() {
  const { user, expired, signIn } = useAuth()
  const [signedInOnArrival] = useState(user !== null)
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<SignInValues>({
    resolver: zodResolver(signInSchema),
  })

  // Only a visitor who arrives already signed in is bounced; after submitting, the submit handler navigates.
  if (user !== null && signedInOnArrival) {
    return <Navigate to={homeFor(user.role)} replace />
  }

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const token = await authApi.login(values)
      signIn(token)
      const home = homeFor(token.user.role)
      navigate(from?.startsWith(home) ? from : home, { replace: true })
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ['email', 'password']))
    }
  })

  return (
    <AuthFrame
      title="Sign in"
      footer={<span>New driver? <Link to="/register" className="text-text underline">Create an account</Link></span>}
    >
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        {expired && !formError && (
          <p role="status" className="rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-sm text-warning">
            Your session expired. Sign in again to continue.
          </p>
        )}
        <FormError message={formError} />
        <TextField label="Email" type="email" autoComplete="username" error={errors.email?.message} {...register('email')} />
        <TextField label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
        <Button type="submit" variant="primary" size="lg" busy={isSubmitting} className="mt-2">Sign in</Button>
      </form>
    </AuthFrame>
  )
}

const registerSchema = z.object({
  name: z.string().trim().min(1, 'Enter your name.').max(120, 'Use at most 120 characters.'),
  email: z.string().trim().min(1, 'Enter your email.').email('Enter a valid email address.'),
  password: z.string().min(8, 'Use at least 8 characters.').max(72, 'Use at most 72 characters.'),
})
type RegisterValues = z.infer<typeof registerSchema>

export function RegisterPage() {
  const { user, signIn } = useAuth()
  const [signedInOnArrival] = useState(user !== null)
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
  })

  // Only a visitor who arrives already signed in is bounced; after submitting, the submit handler navigates.
  if (user !== null && signedInOnArrival) {
    return <Navigate to={homeFor(user.role)} replace />
  }

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await authApi.register(values)
      signIn(await authApi.login({ email: values.email, password: values.password }))
      navigate('/driver/garage', { replace: true })
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ['name', 'email', 'password']))
    }
  })

  return (
    <AuthFrame
      title="Create a driver account"
      footer={<span>Have an account? <Link to="/signin" className="text-text underline">Sign in</Link></span>}
    >
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        <FormError message={formError} />
        <TextField label="Name" autoComplete="name" error={errors.name?.message} {...register('name')} />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="8 to 72 characters."
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" variant="primary" size="lg" busy={isSubmitting} className="mt-2">Create account</Button>
      </form>
    </AuthFrame>
  )
}
