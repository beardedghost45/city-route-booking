export type AdminCredentials = {
  username: string
  password: string
}

export type AdminBooking = {
  bookingNumber: string
  origin: string
  destination: string
  travelDate: string
  passengerCount: number
  leadPassengerName: string
  leadPassengerContact: string
  bookingStatus: string
  paymentStatus: string
  refundStatus: string
  createdAt: string
  updatedAt: string
}

export type AdminBookingsPage = {
  items: AdminBooking[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export class AdminApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'AdminApiError'
  }
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

export async function fetchAdminBookings(
  credentials: AdminCredentials,
  page = 0,
  size = 20,
): Promise<AdminBookingsPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}/api/admin/bookings?${query.toString()}`, {
      headers: {
        Accept: 'application/json',
        Authorization: `Basic ${encodeBasicCredentials(credentials)}`,
      },
    })
  } catch {
    throw new AdminApiError(0, 'Could not connect to the admin service. Check the connection and try again.')
  }

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new AdminApiError(response.status, 'Admin authentication failed.')
    }
    throw new AdminApiError(response.status, `Could not load bookings (HTTP ${response.status}). Try again shortly.`)
  }

  const body: unknown = await response.json().catch(() => null)
  if (!isAdminBookingsPage(body)) {
    throw new AdminApiError(response.status, 'The admin service returned an unreadable bookings page.')
  }
  return body
}

function encodeBasicCredentials({ username, password }: AdminCredentials) {
  const bytes = new TextEncoder().encode(`${username}:${password}`)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
}

function isAdminBookingsPage(value: unknown): value is AdminBookingsPage {
  if (!value || typeof value !== 'object') return false
  const page = value as Partial<AdminBookingsPage>
  return Array.isArray(page.items)
    && Number.isInteger(page.page)
    && Number.isInteger(page.size)
    && Number.isInteger(page.totalElements)
    && Number.isInteger(page.totalPages)
}
