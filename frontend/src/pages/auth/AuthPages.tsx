import { zodResolver } from '@hookform/resolvers/zod'
import { useState, type ReactNode } from 'react'
import { useForm, type FieldValues, type Path, type UseFormSetError } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { homeFor, useAuth } from '../../auth/authContext'
import { TextField } from '../../components/Field'
import { DataBadge } from '../../components/StatusChip'
import { Button, FormError } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { ApiError } from '../../services/api'
import { authApi } from '../../services/endpoints'

function AuthFrame({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <main className="grid min-h-dvh place-items-center px-4 py-10">
      <div className="w-full max-w-sm">
        <p className="text-2xl font-semibold tracking-tight">
          Charge<span className="text-accent">Wise</span>
        </p>
        <p className="mt-2 text-sm text-muted">
          Connector matching and grid-power allocation for an EV charging network, with the reason for every decision.
        </p>
        <section className="mt-8 rounded-lg border border-border bg-surface p-6">
          <h1 className="text-lg font-semibold">{title}</h1>
          <div className="mt-5">{children}</div>
        </section>
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
          {footer}
          <DataBadge kind="synthetic" />
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
        <Button type="submit" variant="primary" busy={isSubmitting}>Sign in</Button>
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
        <Button type="submit" variant="primary" busy={isSubmitting}>Create account</Button>
      </form>
    </AuthFrame>
  )
}
