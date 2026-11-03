import { clearSession, loadSession } from '../auth/tokenStore'
import type { ApiErrorBody, FieldError } from '../types/api'

export const API_BASE = '/api/v1'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

export type ApiRequestInit = RequestInit & {
  /** Serialised as the JSON request body. */
  json?: unknown
}

let unauthorizedHandler: (() => void) | null = null

/** Called once when an authenticated request comes back 401: the token expired or was revoked. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return typeof value === 'object' && value !== null
    && typeof (value as ApiErrorBody).code === 'string'
    && typeof (value as ApiErrorBody).message === 'string'
}

async function toApiError(response: Response): Promise<ApiError> {
  const body: unknown = await response.json().catch(() => null)
  if (isApiErrorBody(body)) {
    return new ApiError(response.status, body.code, body.message, body.fieldErrors ?? [])
  }
  return new ApiError(response.status, 'HTTP_ERROR', `Request failed with status ${response.status}`)
}

/** The only place the frontend talks to the backend. Paths are relative to /api/v1. */
export async function apiFetch<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  const { json, ...rest } = init
  const headers = new Headers(rest.headers)
  headers.set('Accept', 'application/json')
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  const session = loadSession()
  if (session) {
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...rest,
      headers,
      body: json !== undefined ? JSON.stringify(json) : rest.body,
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'The ChargeWise backend could not be reached')
  }
  if (!response.ok) {
    if (response.status === 401 && session) {
      clearSession()
      unauthorizedHandler?.()
    }
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}
