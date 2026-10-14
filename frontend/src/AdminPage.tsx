import { useState, type FormEvent } from 'react'
import {
  AdminApiError,
  fetchAdminBookings,
  type AdminBookingsPage,
  type AdminCredentials,
} from './adminApi'

const PAGE_SIZES = [10, 20, 50, 100]

function titleCase(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatDate(value: string) {
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(date)
}

function formatTimestamp(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function statusTone(value: string) {
  if (value === 'CONFIRMED' || value === 'SUCCEEDED') return 'positive'
  if (value === 'CANCELLED' || value === 'FAILED') return 'muted'
  if (value === 'PENDING' || value === 'PENDING_PAYMENT') return 'waiting'
  return 'muted'
}

function AdminPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [credentials, setCredentials] = useState<AdminCredentials | null>(null)
  const [bookingsPage, setBookingsPage] = useState<AdminBookingsPage | null>(null)
  const [pageSize, setPageSize] = useState(20)
  const [authError, setAuthError] = useState('')
  const [credentialsRejected, setCredentialsRejected] = useState(false)
  const [pageError, setPageError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submitCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextCredentials = { username: username.trim(), password }
    if (!nextCredentials.username || !nextCredentials.password) {
      setAuthError('Enter both the admin username and password.')
      return
    }

    setLoading(true)
    setAuthError('')
    setCredentialsRejected(false)
    try {
      const result = await fetchAdminBookings(nextCredentials, 0, pageSize)
      setCredentials(nextCredentials)
      setBookingsPage(result)
      setUsername('')
      setPassword('')
    } catch (error) {
      handleAuthError(error)
    } finally {
      setLoading(false)
    }
  }

  async function loadPage(nextPage: number, nextSize = pageSize) {
    if (!credentials || loading) return
    setLoading(true)
    setPageError('')
    try {
      const result = await fetchAdminBookings(credentials, nextPage, nextSize)
      setBookingsPage(result)
      setPageSize(nextSize)
    } catch (error) {
      if (isAuthenticationError(error)) {
        handleAuthError(error)
      } else {
        setPageError(errorMessage(error))
      }
    } finally {
      setLoading(false)
    }
  }

  function handleAuthError(error: unknown) {
    if (isAuthenticationError(error)) {
      setCredentials(null)
      setBookingsPage(null)
      setUsername('')
      setPassword('')
      setCredentialsRejected(true)
      setAuthError('Those credentials were not accepted. Re-enter them, and if the problem continues ask an operator to confirm the admin account is configured on the server.')
    } else {
      setAuthError(errorMessage(error))
    }
  }

  function signOut() {
    setCredentials(null)
    setBookingsPage(null)
    setUsername('')
    setPassword('')
    setAuthError('')
    setPageError('')
  }

  const authenticated = credentials !== null && bookingsPage !== null
  const lastPage = bookingsPage ? Math.max(0, bookingsPage.totalPages - 1) : 0

  return (
    <div className="admin-shell">
      <header className="site-header admin-site-header">
        <a className="brand" href="/" aria-label="City Route home">
          <span className="brand-mark" aria-hidden="true">CR</span>
          <span>city<span className="brand-light">route</span></span>
        </a>
        <span className="admin-tag">ADMIN VIEW <span aria-hidden="true">·</span> READ ONLY</span>
      </header>

      <main className="admin-main">
        <div className="admin-heading">
          <p className="eyebrow">CITY ROUTE OPERATIONS</p>
          <h1>Bookings overview</h1>
          <p>Review booking details and statuses. This dashboard does not change bookings.</p>
        </div>

        {!authenticated ? (
          <section className="admin-auth-card" aria-labelledby="admin-auth-title">
            <div className="admin-lock-mark" aria-hidden="true">⌑</div>
            <p className="step-label">RESTRICTED ACCESS</p>
            <h2 id="admin-auth-title">Admin sign in</h2>
            <p className="admin-auth-copy">Use the admin credentials provided by your operator. They stay in memory only while this page is open.</p>
            {authError && <p id="admin-auth-error" className="admin-error" role="alert">{authError}</p>}
            <form className="admin-auth-form" onSubmit={submitCredentials}>
              <div className="admin-field">
                <label htmlFor="admin-username">Username</label>
                <input
                  id="admin-username"
                  type="text"
                  autoComplete="off"
                  value={username}
                  onChange={(event) => { setUsername(event.target.value); setCredentialsRejected(false); setAuthError('') }}
                  aria-invalid={credentialsRejected}
                  aria-describedby={authError ? 'admin-auth-error' : undefined}
                  required
                />
              </div>
              <div className="admin-field">
                <label htmlFor="admin-password">Password</label>
                <input
                  id="admin-password"
                  type="password"
                  autoComplete="off"
                  value={password}
                  onChange={(event) => { setPassword(event.target.value); setCredentialsRejected(false); setAuthError('') }}
                  aria-invalid={credentialsRejected}
                  aria-describedby={authError ? 'admin-auth-error' : undefined}
                  required
                />
              </div>
              <button className="button button-primary admin-submit" type="submit" disabled={loading}>
                {loading ? 'Connecting…' : 'View bookings'}
                {!loading && <span aria-hidden="true">↗</span>}
              </button>
            </form>
            {loading && <p className="admin-loading" role="status">Checking admin access…</p>}
            <p className="admin-security-note">Protected by HTTP Basic authentication. No credentials are saved in browser storage.</p>
          </section>
        ) : (
          <section className="admin-dashboard" aria-labelledby="admin-table-title" aria-busy={loading}>
            <div className="admin-dashboard-bar">
              <div>
                <p className="step-label">READ ONLY</p>
                <h2 id="admin-table-title">All bookings</h2>
              </div>
              <button className="admin-signout" type="button" onClick={signOut} disabled={loading}>Sign out</button>
            </div>

            <div className="admin-table-tools">
              <p className="admin-result-count" aria-live="polite">
                {bookingsPage.totalElements === 1 ? '1 booking' : `${bookingsPage.totalElements} bookings`} · Newest first
              </p>
              <label htmlFor="page-size">Rows per page
                <select id="page-size" value={pageSize} onChange={(event) => void loadPage(0, Number(event.target.value))} disabled={loading}>
                  {PAGE_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
                </select>
              </label>
            </div>

            {loading && <p className="admin-loading admin-page-loading" role="status">Loading bookings…</p>}
            {pageError && (
              <div className="admin-error-panel" role="alert">
                <p>{pageError}</p>
                <button className="button button-secondary" type="button" onClick={() => void loadPage(bookingsPage.page, pageSize)} disabled={loading}>Retry loading this page</button>
              </div>
            )}

            {bookingsPage.items.length === 0 ? (
              <div className="admin-empty" role="status">
                <span aria-hidden="true">✳</span>
                <h3>No bookings yet</h3>
                <p>Bookings will appear here once a customer creates one.</p>
              </div>
            ) : (
              <div className="admin-table-viewport" role="region" aria-label="Bookings table" tabIndex={0}>
                <table className="admin-table">
                  <caption className="visually-hidden">Read-only bookings, sorted newest first</caption>
                  <thead>
                    <tr>
                      <th scope="col">Booking</th>
                      <th scope="col">Journey</th>
                      <th scope="col">Travel date</th>
                      <th scope="col">Passenger</th>
                      <th scope="col">Trip status</th>
                      <th scope="col">Payment</th>
                      <th scope="col">Refund</th>
                      <th scope="col">Created</th>
                      <th scope="col">Updated</th>
                    </tr>
                  </thead>
                  <tbody>
                    {bookingsPage.items.map((booking) => (
                      <tr key={booking.bookingNumber}>
                        <th scope="row"><span className="admin-booking-number">{booking.bookingNumber}</span></th>
                        <td><span className="admin-route">{titleCase(booking.origin)} <span aria-hidden="true">→</span> {titleCase(booking.destination)}</span></td>
                        <td>{formatDate(booking.travelDate)}</td>
                        <td>
                          <span className="admin-passenger-name">{booking.leadPassengerName}</span>
                          <span className="admin-contact">{booking.leadPassengerContact}</span>
                          <span className="admin-passenger-count">{booking.passengerCount} {booking.passengerCount === 1 ? 'passenger' : 'passengers'}</span>
                        </td>
                        <td><StatusBadge status={booking.bookingStatus} /></td>
                        <td><StatusBadge status={booking.paymentStatus} /></td>
                        <td><StatusBadge status={booking.refundStatus} /></td>
                        <td><time dateTime={booking.createdAt}>{formatTimestamp(booking.createdAt)}</time></td>
                        <td><time dateTime={booking.updatedAt}>{formatTimestamp(booking.updatedAt)}</time></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <nav className="admin-pagination" aria-label="Bookings pages">
              <button className="button button-secondary" type="button" onClick={() => void loadPage(bookingsPage.page - 1)} disabled={loading || bookingsPage.page <= 0}>Previous</button>
              <span aria-live="polite">Page {bookingsPage.page + 1} of {Math.max(1, bookingsPage.totalPages)}</span>
              <button className="button button-secondary" type="button" onClick={() => void loadPage(bookingsPage.page + 1)} disabled={loading || bookingsPage.page >= lastPage}>Next</button>
            </nav>
          </section>
        )}
      </main>

      <footer className="site-footer admin-footer">
        <span>© City Route</span><span>Read-only operations dashboard</span>
      </footer>
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  return <span className={`admin-status-badge ${statusTone(status)}`}>{titleCase(status)}</span>
}

function isAuthenticationError(error: unknown) {
  return error instanceof AdminApiError && (error.status === 401 || error.status === 403)
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Could not load bookings. Try again shortly.'
}

export default AdminPage
