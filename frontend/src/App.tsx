import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  ApiError,
  CITIES,
  cancelBooking,
  createBooking,
  getBooking,
  recordPayment,
  type Booking,
  type City,
  type CreateBooking,
  type VersionedBooking,
} from './api'

const PENDING_CREATE_KEY = 'city-route-booking.pending-create.v1'

type FormValues = {
  origin: City | ''
  destination: City | ''
  travelDate: string
  passengerCount: string
  leadPassengerName: string
  leadPassengerContact: string
}

type PendingCreate = {
  idempotencyKey: string
  request: CreateBooking
  rejected?: boolean
  lastError?: string
  fieldErrors?: Record<string, string>
}
type Notice = { kind: 'info' | 'error' | 'success'; text: string }

const emptyForm: FormValues = {
  origin: 'DELHI',
  destination: 'MUMBAI',
  travelDate: '',
  passengerCount: '1',
  leadPassengerName: '',
  leadPassengerContact: '',
}

function readPendingCreate(): PendingCreate | null {
  try {
    const value = localStorage.getItem(PENDING_CREATE_KEY)
    if (!value) return null
    const parsed = JSON.parse(value) as PendingCreate
    if (typeof parsed.idempotencyKey !== 'string' || !parsed.request?.travelDate) return null
    return parsed
  } catch {
    return null
  }
}

function savePendingCreate(pending: PendingCreate) {
  try {
    localStorage.setItem(PENDING_CREATE_KEY, JSON.stringify(pending))
  } catch {
    // The in-memory copy still keeps retries safe for the current page session.
  }
}

function clearPendingCreate() {
  try {
    localStorage.removeItem(PENDING_CREATE_KEY)
  } catch {
    // Storage can be disabled; a confirmed booking can still be shown in memory.
  }
}

function toForm(request: CreateBooking): FormValues {
  return {
    ...request,
    passengerCount: String(request.passengerCount),
  }
}

function toRequest(values: FormValues): CreateBooking {
  return {
    origin: values.origin as City,
    destination: values.destination as City,
    travelDate: values.travelDate,
    passengerCount: Number(values.passengerCount),
    leadPassengerName: values.leadPassengerName.trim(),
    leadPassengerContact: values.leadPassengerContact.trim(),
  }
}

function makeIdempotencyKey() {
  return `city-route-${crypto.randomUUID()}`
}

function cityName(city: string) {
  return city.charAt(0) + city.slice(1).toLowerCase()
}

function statusName(status: string) {
  return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function App() {
  const initialPending = useMemo(readPendingCreate, [])
  const [form, setForm] = useState<FormValues>(() => initialPending ? toForm(initialPending.request) : emptyForm)
  const [pendingCreate, setPendingCreate] = useState<PendingCreate | null>(initialPending)
  const [retryMode, setRetryMode] = useState(Boolean(initialPending && !initialPending.rejected))
  const [current, setCurrent] = useState<VersionedBooking | null>(null)
  const [formErrors, setFormErrors] = useState<Record<string, string>>(() => initialPending?.fieldErrors || {})
  const [notice, setNotice] = useState<Notice | null>(initialPending && !initialPending.rejected ? {
    kind: 'info',
    text: 'A previous booking attempt has not been confirmed yet. Retry with the saved details so we can safely return its result.',
  } : initialPending?.rejected ? {
    kind: 'error',
    text: initialPending.lastError || 'The previous attempt was rejected. Correct any highlighted details before submitting again.',
  } : null)
  const [creating, setCreating] = useState(false)
  const [mutating, setMutating] = useState(false)
  const [loadingLatest, setLoadingLatest] = useState(false)

  useEffect(() => {
    if (current) document.getElementById('booking-details')?.focus()
  }, [current])

  const isSameCity = form.origin !== '' && form.origin === form.destination

  function updateForm(field: keyof FormValues, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }))
    setFormErrors((previous) => ({ ...previous, [field]: '' }))
    if (notice?.kind === 'error') setNotice(null)
  }

  function clientValidation(): Record<string, string> {
    const errors: Record<string, string> = {}
    if (!form.origin) errors.origin = 'Choose a departure city.'
    if (!form.destination) errors.destination = 'Choose an arrival city.'
    if (isSameCity) {
      errors.origin = 'Choose two different cities.'
      errors.destination = 'Choose two different cities.'
    }
    if (!form.travelDate) errors.travelDate = 'Choose a travel date.'
    if (!form.passengerCount || Number(form.passengerCount) < 1 || !Number.isInteger(Number(form.passengerCount))) {
      errors.passengerCount = 'Enter at least one passenger.'
    }
    if (!form.leadPassengerName.trim()) errors.leadPassengerName = 'Enter the lead passenger name.'
    if (!form.leadPassengerContact.trim()) errors.leadPassengerContact = 'Enter an email or phone contact.'
    return errors
  }

  async function submitBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (creating || retryMode) {
      if (retryMode) await retryBooking()
      return
    }

    const errors = clientValidation()
    setFormErrors(errors)
    if (Object.keys(errors).length) {
      setNotice({ kind: 'error', text: 'Check the highlighted fields and try again.' })
      return
    }

    const request = toRequest(form)
    const previousAttempt = pendingCreate || readPendingCreate()
    const pending: PendingCreate = previousAttempt && !previousAttempt.rejected && sameRequest(previousAttempt.request, request)
      ? previousAttempt
      : { idempotencyKey: makeIdempotencyKey(), request }
    setPendingCreate(pending)
    savePendingCreate(pending)
    await sendCreate(pending)
  }

  async function retryBooking() {
    const pending = pendingCreate || readPendingCreate()
    if (!pending) {
      setRetryMode(false)
      setNotice({ kind: 'error', text: 'The saved retry details are unavailable. Review the form and submit again.' })
      return
    }
    await sendCreate(pending)
  }

  async function sendCreate(pending: PendingCreate) {
    setCreating(true)
    setFormErrors({})
    setNotice({ kind: 'info', text: 'Submitting securely with a saved retry key…' })
    try {
      const created = await createBooking(pending.request, pending.idempotencyKey)
      clearPendingCreate()
      setPendingCreate(null)
      setRetryMode(false)
      setCurrent(created)
      setNotice({ kind: 'success', text: 'Your booking is ready.' })
    } catch (error) {
      if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
        const mappedErrors = normalizeFieldErrors(error.fieldErrors)
        const rejectedAttempt = { ...pending, rejected: true, lastError: error.message, fieldErrors: mappedErrors }
        setPendingCreate(rejectedAttempt)
        savePendingCreate(rejectedAttempt)
        setFormErrors(mappedErrors)
        setRetryMode(false)
        setNotice({
          kind: 'error',
          text: error.message || 'The booking details were not accepted. Correct the fields below and try again.',
        })
      } else {
        setPendingCreate(pending)
        setRetryMode(true)
        setNotice({ kind: 'error', text: errorMessage(error) })
      }
    } finally {
      setCreating(false)
    }
  }

  async function mutate(action: 'payment-success' | 'payment-failure' | 'cancel') {
    if (!current || mutating) return
    setMutating(true)
    setNotice({ kind: 'info', text: 'Updating booking…' })
    try {
      const latest = action === 'cancel'
        ? await cancelBooking(current.booking.bookingNumber, current.etag)
        : await recordPayment(current.booking.bookingNumber, current.etag, action === 'payment-success' ? 'SUCCESS' : 'FAILURE')
      setCurrent(latest)
      setNotice({ kind: 'success', text: action === 'cancel' ? cancellationMessage(latest.booking) : 'Payment simulation updated.' })
    } catch (error) {
      if (error instanceof ApiError && error.status === 412) {
        setLoadingLatest(true)
        try {
          const refreshed = await getBooking(current.booking.bookingNumber)
          setCurrent(refreshed)
          setNotice({
            kind: 'info',
            text: 'This booking changed before your update was saved. We refreshed the latest details. Review the current status before trying again.',
          })
        } catch (refreshError) {
          setNotice({ kind: 'error', text: `This booking changed before your update was saved. We could not refresh it: ${errorMessage(refreshError)}` })
        } finally {
          setLoadingLatest(false)
        }
      } else {
        setNotice({ kind: 'error', text: errorMessage(error) })
      }
    } finally {
      setMutating(false)
    }
  }

  function startAnotherBooking() {
    setCurrent(null)
    setForm(emptyForm)
    setFormErrors({})
    setNotice(null)
  }

  const booking = current?.booking
  const formLocked = retryMode || creating

  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="City Route home">
          <span className="brand-mark" aria-hidden="true">CR</span>
          <span>city<span className="brand-light">route</span></span>
        </a>
        <div className="header-note"><span className="live-dot" aria-hidden="true" /> Routes across India</div>
      </header>

      <main id="top" className="main-content">
        <section className="intro" aria-labelledby="page-title">
          <p className="eyebrow">A simpler way to get there</p>
          <h1 id="page-title">Make room for<br /><em>the journey.</em></h1>
          <p className="intro-copy">Choose your cities, set your date, and let the good part begin.</p>
          <div className="route-line" aria-label="Five destinations available">
            <span className="route-stop" /><span className="route-track" /><span className="route-stop route-stop-filled" />
            <span className="route-caption">5 cities · 20 ways to go</span>
          </div>
        </section>

        <section className="booking-card" aria-labelledby="form-title">
          {!booking ? (
            <>
              <div className="card-heading">
                <div>
                  <p className="step-label">YOUR TRIP <span>01 / 01</span></p>
                  <h2 id="form-title">Where are you headed?</h2>
                </div>
                <div className="plane-illustration" aria-hidden="true">✳</div>
              </div>

              {notice && <NoticeBanner notice={notice} />}

              {retryMode && (
                <div className="retry-panel">
                  <strong>Booking status needs a check</strong>
                  <p>Your saved trip and retry key are ready. Repeating this request will return the same booking if it was already created.</p>
                  <button className="button button-primary retry-button" type="button" onClick={retryBooking} disabled={creating}>
                    {creating ? 'Checking…' : 'Retry the saved booking'}
                  </button>
                </div>
              )}

              <form onSubmit={submitBooking} noValidate>
                <fieldset disabled={formLocked}>
                  <legend className="visually-hidden">Booking details</legend>
                  <div className="route-fields">
                    <div className="field field-city">
                      <label htmlFor="origin">From</label>
                      <select id="origin" value={form.origin} onChange={(event) => updateForm('origin', event.target.value)} aria-invalid={Boolean(formErrors.origin)} aria-describedby={formErrors.origin ? 'origin-error' : undefined}>
                        <option value="">Select city</option>
                        {CITIES.map((city) => <option key={city} value={city}>{cityName(city)}</option>)}
                      </select>
                      {formErrors.origin && <FieldError id="origin-error" text={formErrors.origin} />}
                    </div>
                    <div className="swap-mark" aria-hidden="true">↔</div>
                    <div className="field field-city">
                      <label htmlFor="destination">To</label>
                      <select id="destination" value={form.destination} onChange={(event) => updateForm('destination', event.target.value)} aria-invalid={Boolean(formErrors.destination)} aria-describedby={formErrors.destination ? 'destination-error' : undefined}>
                        <option value="">Select city</option>
                        {CITIES.map((city) => <option key={city} value={city}>{cityName(city)}</option>)}
                      </select>
                      {formErrors.destination && <FieldError id="destination-error" text={formErrors.destination} />}
                    </div>
                  </div>
                  {isSameCity && !formLocked && <p className="inline-help" role="status">Choose two different cities for your route.</p>}

                  <div className="field-grid">
                    <div className="field">
                      <label htmlFor="travelDate">Travel date</label>
                      <input id="travelDate" type="date" value={form.travelDate} onChange={(event) => updateForm('travelDate', event.target.value)} aria-invalid={Boolean(formErrors.travelDate)} aria-describedby={formErrors.travelDate ? 'date-error' : undefined} />
                      {formErrors.travelDate && <FieldError id="date-error" text={formErrors.travelDate} />}
                    </div>
                    <div className="field">
                      <label htmlFor="passengerCount">Passengers</label>
                      <input id="passengerCount" type="number" min="1" step="1" inputMode="numeric" value={form.passengerCount} onChange={(event) => updateForm('passengerCount', event.target.value)} aria-invalid={Boolean(formErrors.passengerCount)} aria-describedby={formErrors.passengerCount ? 'passengers-error' : 'passengers-help'} />
                      {formErrors.passengerCount ? <FieldError id="passengers-error" text={formErrors.passengerCount} /> : <span id="passengers-help" className="field-hint">No seat limit for this demo</span>}
                    </div>
                  </div>

                  <div className="field-grid">
                    <div className="field">
                      <label htmlFor="leadPassengerName">Lead passenger</label>
                      <input id="leadPassengerName" type="text" autoComplete="name" maxLength={200} placeholder="Full name" value={form.leadPassengerName} onChange={(event) => updateForm('leadPassengerName', event.target.value)} aria-invalid={Boolean(formErrors.leadPassengerName)} aria-describedby={formErrors.leadPassengerName ? 'name-error' : undefined} />
                      {formErrors.leadPassengerName && <FieldError id="name-error" text={formErrors.leadPassengerName} />}
                    </div>
                    <div className="field">
                      <label htmlFor="leadPassengerContact">Contact</label>
                      <input id="leadPassengerContact" type="text" autoComplete="email" maxLength={254} placeholder="Email or phone" value={form.leadPassengerContact} onChange={(event) => updateForm('leadPassengerContact', event.target.value)} aria-invalid={Boolean(formErrors.leadPassengerContact)} aria-describedby={formErrors.leadPassengerContact ? 'contact-error' : undefined} />
                      {formErrors.leadPassengerContact && <FieldError id="contact-error" text={formErrors.leadPassengerContact} />}
                    </div>
                  </div>
                </fieldset>

                {!retryMode && (
                  <button className="button button-primary create-button" type="submit" disabled={creating}>
                    {creating ? 'Creating your booking…' : <>Create booking <span aria-hidden="true">↗</span></>}
                  </button>
                )}
              </form>
              <p className="privacy-note"><span aria-hidden="true">⌁</span> Your booking number is your reference. Keep it handy.</p>
            </>
          ) : (
            <BookingDetails
              booking={booking}
              pending={mutating || loadingLatest}
              notice={notice}
              onPaymentSuccess={() => mutate('payment-success')}
              onPaymentFailure={() => mutate('payment-failure')}
              onCancel={() => mutate('cancel')}
              onNewBooking={startAnotherBooking}
            />
          )}
        </section>

        <section className="city-strip" aria-label="Available destinations">
          <span className="strip-label">GO WHERE YOU WANT</span>
          <div className="city-list">{CITIES.map((city) => <span key={city}>{cityName(city)}</span>)}</div>
        </section>

        <aside className="demo-note" aria-label="Payment simulation notice">
          <span className="demo-icon" aria-hidden="true">i</span>
          <p><strong>Demo experience.</strong> Payment and refund statuses are simulations only. No money is charged or refunded.</p>
        </aside>
      </main>

      <footer className="site-footer">
        <span>© City Route</span><span>Take the long way, if you like.</span>
      </footer>
    </div>
  )
}

function BookingDetails({
  booking,
  pending,
  notice,
  onPaymentSuccess,
  onPaymentFailure,
  onCancel,
  onNewBooking,
}: {
  booking: Booking
  pending: boolean
  notice: Notice | null
  onPaymentSuccess: () => void
  onPaymentFailure: () => void
  onCancel: () => void
  onNewBooking: () => void
}) {
  return (
    <div id="booking-details" className="confirmation" tabIndex={-1} aria-labelledby="confirmation-title">
      <div className="confirmation-top">
        <span className="confirmation-check" aria-hidden="true">✓</span>
        <p className="step-label">BOOKING CREATED</p>
        <h2 id="confirmation-title">You’re on your way.</h2>
        <p className="confirmation-copy">Save this reference for your journey.</p>
      </div>

      {notice && <NoticeBanner notice={notice} />}

      <div className="booking-reference">
        <span>BOOKING REFERENCE</span>
        <strong>{booking.bookingNumber}</strong>
      </div>

      <div className="journey-card">
        <div className="journey-cities">
          <div><span>FROM</span><strong>{cityName(booking.origin)}</strong></div>
          <span className="journey-arrow" aria-hidden="true">→</span>
          <div className="journey-destination"><span>TO</span><strong>{cityName(booking.destination)}</strong></div>
        </div>
        <div className="journey-meta">
          <div><span>DATE</span><strong>{formatDate(booking.travelDate)}</strong></div>
          <div><span>PASSENGERS</span><strong>{booking.passengerCount}</strong></div>
        </div>
      </div>

      <section className="status-section" aria-labelledby="status-title">
        <div className="status-heading"><h3 id="status-title">Booking status</h3><span className="updated-time">Live details</span></div>
        <dl className="status-list" aria-live="polite" aria-atomic="true">
          <StatusRow label="Trip" value={booking.bookingStatus} />
          <StatusRow label="Payment" value={booking.paymentStatus} />
          <StatusRow label="Refund" value={booking.refundStatus} />
        </dl>
      </section>

      <div className="simulation-callout">
        <span aria-hidden="true">✳</span>
        <p><strong>Payment is simulated.</strong> These controls only change the demo status. They will not charge or refund money.</p>
      </div>

      {booking.bookingStatus === 'PENDING_PAYMENT' && (
        <div className="action-group" aria-label="Payment simulation controls">
          <p className="action-label">TRY A PAYMENT RESULT</p>
          <div className="button-row">
            <button className="button button-primary" type="button" onClick={onPaymentSuccess} disabled={pending}>Simulate success</button>
            <button className="button button-secondary" type="button" onClick={onPaymentFailure} disabled={pending}>Simulate failure</button>
          </div>
          <p className="field-hint">A failed attempt can be tried again.</p>
        </div>
      )}

      {booking.bookingStatus !== 'CANCELLED' ? (
        <button className="cancel-button" type="button" onClick={onCancel} disabled={pending}>
          {pending ? 'Updating…' : 'Cancel this booking'}
        </button>
      ) : (
        <p className="cancelled-note" role="status">
          {booking.refundStatus === 'PENDING'
            ? 'A simulated refund is marked as pending. No money will be returned.'
            : 'This booking is cancelled. No payment or refund was recorded.'}
        </p>
      )}

      <button className="new-booking-link" type="button" onClick={onNewBooking} disabled={pending}>Create another booking <span aria-hidden="true">↗</span></button>
    </div>
  )
}

function StatusRow({ label, value }: { label: string; value: string }) {
  const tone = value === 'CONFIRMED' || value === 'SUCCEEDED'
    ? 'positive'
    : value === 'CANCELLED' || value === 'FAILED'
      ? 'neutral'
      : value === 'PENDING'
        ? 'waiting'
        : 'quiet'
  return (
    <div className="status-row">
      <dt>{label}</dt>
      <dd><span className={`status-pill ${tone}`}>{statusName(value)}</span></dd>
    </div>
  )
}

function NoticeBanner({ notice }: { notice: Notice }) {
  return <div className={`notice notice-${notice.kind}`} role={notice.kind === 'error' ? 'alert' : 'status'} aria-live={notice.kind === 'error' ? 'assertive' : 'polite'}>{notice.text}</div>
}

function FieldError({ id, text }: { id: string; text: string }) {
  return <span className="field-error" id={id}>{text}</span>
}

function normalizeFieldErrors(errors: Record<string, string>) {
  const names: Record<string, string> = {
    origin: 'origin',
    destination: 'destination',
    travelDate: 'travelDate',
    passengerCount: 'passengerCount',
    leadPassengerName: 'leadPassengerName',
    leadPassengerContact: 'leadPassengerContact',
  }
  return Object.fromEntries(Object.entries(errors).map(([key, value]) => [names[key] || key, value]))
}

function sameRequest(left: CreateBooking, right: CreateBooking) {
  return JSON.stringify(left) === JSON.stringify(right)
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function cancellationMessage(booking: Booking) {
  return booking.refundStatus === 'PENDING'
    ? 'Booking cancelled. A refund is marked pending in this simulation; no money will be refunded.'
    : 'Booking cancelled. No refund was recorded because payment had not succeeded.'
}

function formatDate(value: string) {
  const parsed = new Date(`${value}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(parsed)
}

export default App
