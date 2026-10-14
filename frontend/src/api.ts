export const CITIES = ['DELHI', 'MUMBAI', 'HYDERABAD', 'KOLKATA', 'BENGALURU'] as const
export type City = (typeof CITIES)[number]

export type Booking = {
  bookingNumber: string
  origin: City
  destination: City
  travelDate: string
  passengerCount: number
  leadPassengerName: string
  leadPassengerContact: string
  bookingStatus: 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED'
  paymentStatus: 'PENDING' | 'SUCCEEDED' | 'FAILED'
  refundStatus: 'NONE' | 'PENDING'
  createdAt: string
  updatedAt: string
}

export type CreateBooking = {
  origin: City
  destination: City
  travelDate: string
  passengerCount: number
  leadPassengerName: string
  leadPassengerContact: string
}

export type ApiErrorBody = {
  status?: number
  code?: string
  message?: string
  fieldErrors?: Record<string, string>
}

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: Record<string, string>

  constructor(status: number, body: ApiErrorBody) {
    super(body.message || `The request failed (${status}).`)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code || 'request_failed'
    this.fieldErrors = body.fieldErrors || {}
  }
}

export type VersionedBooking = { booking: Booking; etag: string }

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const bookingPath = '/api/bookings'

async function send<T>(path: string, init: RequestInit = {}): Promise<{ data: T; etag: string | null }> {
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
  } catch {
    throw new Error('We could not reach the booking service. Your request may still have arrived. Retry safely to check its status.')
  }

  const body = await response.json().catch(() => null) as T | ApiErrorBody | null
  if (!response.ok) {
    const apiError = (body && typeof body === 'object' ? body : {}) as ApiErrorBody
    throw new ApiError(response.status, apiError)
  }
  if (body === null) throw new Error('The booking service returned an unreadable response.')

  return { data: body as T, etag: response.headers.get('ETag') }
}

export async function createBooking(request: CreateBooking, idempotencyKey: string) {
  const response = await send<Booking>(bookingPath, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(request),
  })
  return versioned(response.data, response.etag)
}

export async function getBooking(bookingNumber: string) {
  const response = await send<Booking>(`${bookingPath}/${encodeURIComponent(bookingNumber)}`)
  return versioned(response.data, response.etag)
}

export async function recordPayment(bookingNumber: string, etag: string, result: 'SUCCESS' | 'FAILURE') {
  const response = await send<Booking>(`${bookingPath}/${encodeURIComponent(bookingNumber)}/payment`, {
    method: 'POST',
    headers: { 'If-Match': etag },
    body: JSON.stringify({ result }),
  })
  return versioned(response.data, response.etag)
}

export async function cancelBooking(bookingNumber: string, etag: string) {
  const response = await send<Booking>(`${bookingPath}/${encodeURIComponent(bookingNumber)}/cancellation`, {
    method: 'POST',
    headers: { 'If-Match': etag },
  })
  return versioned(response.data, response.etag)
}

function versioned(booking: Booking, etag: string | null): VersionedBooking {
  if (!etag) throw new Error('The booking service did not return an ETag, so updates are unavailable. Refresh the booking or try again later.')
  return { booking, etag }
}
