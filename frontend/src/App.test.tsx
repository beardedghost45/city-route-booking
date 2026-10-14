import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Booking } from './api'

const bookingNumber = 'BK-opaque-reference-42'
const initialEtag = `"${bookingNumber}-v0"`

function booking(overrides: Partial<Booking> = {}): Booking {
  return {
    bookingNumber,
    origin: 'DELHI',
    destination: 'MUMBAI',
    travelDate: '2027-03-14',
    passengerCount: 2,
    leadPassengerName: 'Ada Lovelace',
    leadPassengerContact: 'ada@example.test',
    bookingStatus: 'PENDING_PAYMENT',
    paymentStatus: 'PENDING',
    refundStatus: 'NONE',
    createdAt: '2026-10-14T10:00:00Z',
    updatedAt: '2026-10-14T10:00:00Z',
    ...overrides,
  }
}

function jsonResponse(body: unknown, status = 200, etag?: string) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...(etag ? { ETag: etag } : {}),
    },
  })
}

function requestHeader(call: number, header: string) {
  const [, init] = vi.mocked(fetch).mock.calls[call]
  return new Headers(init?.headers).get(header)
}

function requestBody(call: number) {
  const [, init] = vi.mocked(fetch).mock.calls[call]
  return JSON.parse(String(init?.body)) as Record<string, unknown>
}

async function completeRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Travel date'), '2027-03-14')
  await user.clear(screen.getByLabelText('Lead passenger'))
  await user.type(screen.getByLabelText('Lead passenger'), 'Ada Lovelace')
  await user.type(screen.getByLabelText('Contact'), 'ada@example.test')
}

async function createBooking(user: ReturnType<typeof userEvent.setup>, response = jsonResponse(booking(), 201, initialEtag)) {
  vi.mocked(fetch).mockResolvedValueOnce(response)
  await completeRequiredFields(user)
  await user.click(screen.getByRole('button', { name: /create booking/i }))
  await screen.findByText(bookingNumber)
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('booking interface', () => {
  it('offers only the five supported cities and validates required fields and distinct routes', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn())
    render(<App />)

    const cityOptions = within(screen.getByLabelText('From')).getAllByRole('option')
      .filter((option) => (option as HTMLOptionElement).value !== '')
      .map((option) => (option as HTMLOptionElement).value)
    expect(cityOptions).toEqual(['DELHI', 'MUMBAI', 'HYDERABAD', 'KOLKATA', 'BENGALURU'])
    expect(within(screen.getByLabelText('To')).getAllByRole('option'))
      .toHaveLength(cityOptions.length + 1)

    await user.clear(screen.getByLabelText('Passengers'))
    await user.click(screen.getByRole('button', { name: /create booking/i }))
    expect(screen.getByText('Choose a travel date.')).toBeInTheDocument()
    expect(screen.getByText('Enter at least one passenger.')).toBeInTheDocument()
    expect(screen.getByText('Enter the lead passenger name.')).toBeInTheDocument()
    expect(screen.getByText('Enter an email or phone contact.')).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()

    await user.type(screen.getByLabelText('Passengers'), '1')
    await user.selectOptions(screen.getByLabelText('To'), 'DELHI')
    expect(screen.getByRole('status')).toHaveTextContent('Choose two different cities')
    await completeRequiredFields(user)
    await user.click(screen.getByRole('button', { name: /create booking/i }))
    expect(screen.getAllByText('Choose two different cities.')).toHaveLength(2)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('shows API field validation errors accessibly', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      status: 400,
      code: 'validation_failed',
      message: 'The origin city is not supported.',
      fieldErrors: { origin: 'Choose a supported departure city.' },
    }, 400)))
    render(<App />)

    await completeRequiredFields(user)
    await user.click(screen.getByRole('button', { name: /create booking/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The origin city is not supported.')
    expect(screen.getByText('Choose a supported departure city.')).toBeInTheDocument()
    expect(screen.getByLabelText('From')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows the opaque booking reference and current booking, payment, and refund statuses', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn())
    render(<App />)
    await createBooking(user)

    expect(screen.getByText(bookingNumber)).toBeInTheDocument()
    expect(screen.getByText('Pending Payment')).toBeInTheDocument()
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByText('None')).toBeInTheDocument()
    expect(screen.queryByText(/database|internal id/i)).not.toBeInTheDocument()
    expect(screen.getByText(/payment and refund statuses are simulations only/i)).toBeInTheDocument()
  })

  it('retries an uncertain create with the same persisted idempotency key', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('connection dropped'))
      .mockResolvedValueOnce(jsonResponse(booking(), 200, initialEtag))
    vi.stubGlobal('fetch', fetchMock)
    const { unmount } = render(<App />)

    await completeRequiredFields(user)
    await user.click(screen.getByRole('button', { name: /create booking/i }))
    expect(await screen.findByRole('button', { name: /retry the saved booking/i })).toBeInTheDocument()

    const pending = JSON.parse(localStorage.getItem('city-route-booking.pending-create.v1') || 'null')
    expect(pending).toEqual(expect.objectContaining({
      idempotencyKey: expect.any(String),
      request: expect.objectContaining({ travelDate: '2027-03-14', leadPassengerName: 'Ada Lovelace' }),
    }))
    const savedKey = pending.idempotencyKey as string
    unmount()

    render(<App />)
    await user.click(await screen.findByRole('button', { name: /retry the saved booking/i }))

    expect(await screen.findByText(bookingNumber)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(requestHeader(0, 'Idempotency-Key')).toBe(savedKey)
    expect(requestHeader(1, 'Idempotency-Key')).toBe(savedKey)
    expect(JSON.parse(localStorage.getItem('city-route-booking.pending-create.v1') || 'null')).toBeNull()
  })

  it('sends the latest ETag for each simulated payment and cancellation and shows paid refund-pending', async () => {
    const user = userEvent.setup()
    const paid = booking({ bookingStatus: 'CONFIRMED', paymentStatus: 'SUCCEEDED' })
    const cancelled = booking({ bookingStatus: 'CANCELLED', paymentStatus: 'SUCCEEDED', refundStatus: 'PENDING' })
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(booking(), 201, initialEtag))
      .mockResolvedValueOnce(jsonResponse(booking({ paymentStatus: 'FAILED' }), 200, `"${bookingNumber}-v1"`))
      .mockResolvedValueOnce(jsonResponse(paid, 200, `"${bookingNumber}-v2"`))
      .mockResolvedValueOnce(jsonResponse(cancelled, 200, `"${bookingNumber}-v3"`)))
    render(<App />)
    await createBooking(user)

    await user.click(screen.getByRole('button', { name: 'Simulate failure' }))
    await waitFor(() => expect(screen.getByText('Failed')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Simulate success' }))
    await waitFor(() => expect(screen.getByText('Confirmed')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Cancel this booking' }))

    await waitFor(() => expect(screen.getByText(/simulated refund is marked as pending/i)).toBeInTheDocument())
    expect(requestHeader(1, 'If-Match')).toBe(initialEtag)
    expect(requestBody(1)).toEqual({ result: 'FAILURE' })
    expect(requestHeader(2, 'If-Match')).toBe(`"${bookingNumber}-v1"`)
    expect(requestBody(2)).toEqual({ result: 'SUCCESS' })
    expect(requestHeader(3, 'If-Match')).toBe(`"${bookingNumber}-v2"`)
    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
  })

  it('does not claim a refund when an unpaid booking is cancelled', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(booking(), 201, initialEtag))
      .mockResolvedValueOnce(jsonResponse(booking({ bookingStatus: 'CANCELLED' }), 200, `"${bookingNumber}-v1"`)))
    render(<App />)
    await createBooking(user)
    await user.click(screen.getByRole('button', { name: 'Cancel this booking' }))

    expect(await screen.findByText(/no payment or refund was recorded/i)).toBeInTheDocument()
    expect(screen.getByText('None')).toBeInTheDocument()
    expect(requestHeader(1, 'If-Match')).toBe(initialEtag)
  })

  it('refreshes on a stale ETag and explains that the booking changed', async () => {
    const user = userEvent.setup()
    const latest = booking({ bookingStatus: 'CONFIRMED', paymentStatus: 'SUCCEEDED' })
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(booking(), 201, initialEtag))
      .mockResolvedValueOnce(jsonResponse({
        status: 412,
        code: 'precondition_failed',
        message: 'The booking changed after the supplied If-Match tag was issued',
        fieldErrors: {},
      }, 412))
      .mockResolvedValueOnce(jsonResponse(latest, 200, `"${bookingNumber}-v1"`)))
    render(<App />)
    await createBooking(user)
    await user.click(screen.getByRole('button', { name: 'Simulate success' }))

    expect(await screen.findByText(/booking changed before your update was saved/i)).toBeInTheDocument()
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
    expect(screen.getByText('Succeeded')).toBeInTheDocument()
    expect(requestHeader(1, 'If-Match')).toBe(initialEtag)
    expect(vi.mocked(fetch).mock.calls[2][0]).toContain(`/api/bookings/${bookingNumber}`)
  })
})
