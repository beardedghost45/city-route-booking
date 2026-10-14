import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'
import type { AdminBooking, AdminBookingsPage } from './adminApi'

function booking(overrides: Partial<AdminBooking> = {}): AdminBooking {
  return {
    bookingNumber: 'BK-admin-100',
    origin: 'DELHI',
    destination: 'MUMBAI',
    travelDate: '2027-03-14',
    passengerCount: 2,
    leadPassengerName: 'Ada Lovelace',
    leadPassengerContact: 'ada@example.test',
    bookingStatus: 'CONFIRMED',
    paymentStatus: 'SUCCEEDED',
    refundStatus: 'NONE',
    createdAt: '2026-10-14T10:00:00Z',
    updatedAt: '2026-10-14T10:15:00Z',
    ...overrides,
  }
}

function page(items: AdminBooking[], overrides: Partial<AdminBookingsPage> = {}): AdminBookingsPage {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
    ...overrides,
  }
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function requestUrl(call: number) {
  return String(vi.mocked(fetch).mock.calls[call][0])
}

function requestHeader(call: number, header: string) {
  const [, init] = vi.mocked(fetch).mock.calls[call]
  return new Headers(init?.headers).get(header)
}

async function fillCredentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Username'), 'ops@example.test')
  await user.type(screen.getByLabelText('Password'), 'demo-secret')
}

async function authenticate(user: ReturnType<typeof userEvent.setup>) {
  await fillCredentials(user)
  await user.click(screen.getByRole('button', { name: 'View bookings' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('admin bookings dashboard', () => {
  it('loads an empty first page with Basic auth and the default zero-based pagination', async () => {
    const user = userEvent.setup()
    let finishRequest: ((response: Response) => void) | undefined
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => { finishRequest = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    localStorage.setItem('admin-storage-check', 'unchanged')
    sessionStorage.setItem('admin-session-check', 'unchanged')
    render(<AdminPage />)

    expect(screen.getByLabelText('Username')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
    await authenticate(user)

    expect(screen.getByRole('status')).toHaveTextContent('Checking admin access')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(requestUrl(0)).toContain('/api/admin/bookings?page=0&size=20')
    expect(requestHeader(0, 'Authorization')).toBe(`Basic ${btoa('ops@example.test:demo-secret')}`)
    expect(requestHeader(0, 'Content-Type')).toBeNull()

    finishRequest?.(jsonResponse(page([])))
    expect(await screen.findByRole('heading', { name: 'No bookings yet' })).toBeInTheDocument()
    expect(screen.getByText('0 bookings · Newest first')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Bookings pages' })).toBeInTheDocument()
    expect(localStorage.getItem('admin-storage-check')).toBe('unchanged')
    expect(sessionStorage.getItem('admin-session-check')).toBe('unchanged')
    expect(localStorage.getItem('admin-credentials')).toBeNull()
    expect(sessionStorage.getItem('admin-credentials')).toBeNull()
  })

  it('clears credentials after invalid authentication and gives an actionable message', async () => {
    const user = userEvent.setup()
    localStorage.clear()
    sessionStorage.clear()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401)))
    render(<AdminPage />)

    await authenticate(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(/credentials were not accepted/i)
    expect(screen.getByRole('alert')).toHaveTextContent(/admin account is configured on the server/i)
    expect(screen.getByLabelText('Username')).toHaveValue('')
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('shows an accessible server error and lets the admin retry', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse({ message: 'Internal failure' }, 500))
      .mockResolvedValueOnce(jsonResponse(page([booking()]))))
    render(<AdminPage />)

    await authenticate(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load bookings (HTTP 500)')
    expect(screen.getByLabelText('Username')).toHaveValue('ops@example.test')
    expect(screen.getByLabelText('Password')).toHaveValue('demo-secret')
    await user.click(screen.getByRole('button', { name: 'View bookings' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.getByText('BK-admin-100')).toBeInTheDocument()
  })

  it('displays public booking details in a read-only paginated table', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(page([booking()])))
    )
    render(<AdminPage />)
    await authenticate(user)

    const table = await screen.findByRole('table')
    expect(within(table).getByText('BK-admin-100')).toBeInTheDocument()
    expect(within(table).getByText((_, element) => Boolean(element?.classList.contains('admin-route')))).toHaveTextContent(/Delhi.*Mumbai/)
    expect(within(table).getByText('Ada Lovelace')).toBeInTheDocument()
    expect(within(table).getByText('ada@example.test')).toBeInTheDocument()
    expect(within(table).getByText('2 passengers')).toBeInTheDocument()
    expect(within(table).getByText('Confirmed')).toBeInTheDocument()
    expect(within(table).getByText('Succeeded')).toBeInTheDocument()
    expect(within(table).getByText('None')).toBeInTheDocument()
    expect(within(table).queryByText(/request fingerprint|database id|idempotency key/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cancel|payment|refund/i })).not.toBeInTheDocument()
    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument()
  })

  it('requests the next page and a new page size from the server', async () => {
    const user = userEvent.setup()
    const first = booking({ bookingNumber: 'BK-page-one' })
    const second = booking({ bookingNumber: 'BK-page-two', origin: 'KOLKATA', destination: 'BENGALURU' })
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(page([first], { totalElements: 21, totalPages: 2 })))
      .mockResolvedValueOnce(jsonResponse(page([second], { page: 1, totalElements: 21, totalPages: 2 })))
      .mockResolvedValueOnce(jsonResponse(page([first], { size: 10, totalElements: 21, totalPages: 3 }))))
    render(<AdminPage />)
    await authenticate(user)

    await user.click(await screen.findByRole('button', { name: 'Next' }))
    expect(await screen.findByText('BK-page-two')).toBeInTheDocument()
    expect(requestUrl(1)).toContain('/api/admin/bookings?page=1&size=20')
    expect(requestHeader(1, 'Authorization')).toBe(`Basic ${btoa('ops@example.test:demo-secret')}`)
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Rows per page'), '10')
    await waitFor(() => expect(screen.getByText('BK-page-one')).toBeInTheDocument())
    expect(requestUrl(2)).toContain('/api/admin/bookings?page=0&size=10')
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
  })
})
