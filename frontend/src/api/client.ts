// Thin fetch wrapper. Two jobs: attach the fake-login header, and preserve the
// server's SPECIFIC error reason. Screens must be able to show the exact reason
// and message the middleware returned — never a generic "something went wrong".

import type { ErrorBody } from '../types'

const BASE_URL: string = import.meta.env.VITE_API_BASE ?? '/api'

// Held in memory only, mirrored here from the session context. No localStorage,
// no sessionStorage — a page reload logs you out, which is intended.
let currentCustomerId: number | null = null

export function setSessionCustomerId(customerId: number | null): void {
  currentCustomerId = customerId
}

/** An error carrying the server's {reason, message} contract. */
export class ApiError extends Error {
  readonly reason: string
  readonly status: number

  constructor(reason: string, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.reason = reason
    this.status = status
  }
}

/** Anything thrown in a screen's catch block, narrowed to something displayable. */
export function toApiError(thrown: unknown): ApiError {
  if (thrown instanceof ApiError) return thrown
  const message = thrown instanceof Error ? thrown.message : String(thrown)
  return new ApiError('UNEXPECTED_ERROR', message, 0)
}

interface RequestOptions {
  method?: string
  body?: unknown
  headers?: Record<string, string>
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {} } = options

  const finalHeaders: Record<string, string> = { Accept: 'application/json', ...headers }
  if (body !== undefined) {
    finalHeaders['Content-Type'] = 'application/json'
  }
  if (currentCustomerId !== null) {
    finalHeaders['X-Customer-Id'] = String(currentCustomerId)
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: finalHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (networkError) {
    // The only case where we have no server reason to show.
    const detail = networkError instanceof Error ? networkError.message : String(networkError)
    throw new ApiError('NETWORK_ERROR', `Could not reach the server: ${detail}`, 0)
  }

  const payload = await readBody(response)

  if (!response.ok) {
    const error = payload as Partial<ErrorBody> | null
    throw new ApiError(
      error?.reason ?? `HTTP_${response.status}`,
      error?.message ?? `Request failed with status ${response.status}.`,
      response.status,
    )
  }

  return payload as T
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { reason: 'MALFORMED_RESPONSE', message: text } satisfies ErrorBody
  }
}

export const api = {
  get: <T>(path: string): Promise<T> => request<T>(path),
  post: <T>(path: string, body?: unknown): Promise<T> => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown): Promise<T> => request<T>(path, { method: 'PUT', body }),
  del: <T>(path: string): Promise<T> => request<T>(path, { method: 'DELETE' }),
}
