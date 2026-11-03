import { errorMessage } from '../lib/errors'
import { ApiError } from '../services/api'
import { Mono } from './ui'

/** A failed request: the server's message, then each field error it named. */
export function ApiErrorAlert({ error }: { error: unknown }) {
  if (!error) {
    return null
  }
  return (
    <div role="alert" className="rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
      <p className="font-medium">{errorMessage(error)}</p>
      {error instanceof ApiError && error.fieldErrors.length > 0 && (
        <ul className="mt-2 flex flex-col gap-1">
          {error.fieldErrors.map((fieldError) => (
            <li key={`${fieldError.field}-${fieldError.message}`}><Mono>{fieldError.field}</Mono>: {fieldError.message}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
