import { ApiError } from '../services/api'

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}
