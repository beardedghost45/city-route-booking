# Gather MVP booking requirements and acceptance criteria

## Context

We are starting a city-to-city booking application with a Java backend and a React frontend. This issue records the approved MVP behavior so implementation can be split into ordered technical issues.

## Confirmed requirements

- Supported cities are Delhi, Mumbai, Hyderabad, Kolkata, and Bengaluru. Every distinct origin/destination pair is valid; origin and destination must differ.
- Required booking fields are travel date, passenger count, lead passenger name, and lead passenger contact.
- Each booking receives a unique opaque booking number prefixed with `BK-`; do not expose an internal database identifier.
- Booking creation accepts an idempotency key. Repeating a create request with the same key must not create a duplicate booking.
- Concurrent updates to one booking use HTTP `ETag`/`If-Match`; reject an update based on a stale tag.
- Payment uses a simulated status flow only. No provider integration or real money movement is in scope. Exact fare calculation is deferred; use a non-financial test amount only if required by the simulated status flow.
- Cancellation marks the booking cancelled. After successful simulated payment, also record refund-pending; no real refund is issued.
- Seat/class selection, seat capacity, transport inventory, authentication, and staff tools are deferred from the MVP.

## Implementation decisions and issue sequence

- Use Spring Boot with Java 17, PostgreSQL, and Flyway; use React, TypeScript, and Vite.
- Idempotency keys are global for this unauthenticated MVP and retained with the booking. Same key plus the same normalized request returns the original booking; same key plus a different request returns HTTP 409.
- New bookings start pending payment. A demo action records simulated success or failure.
- Every state-changing request for an existing booking requires `If-Match`; a missing header returns HTTP 428 and a stale tag returns HTTP 412.
- The orchestrator created ordered issues for the [database foundation](https://github.com/beardedghost45/city-route-booking/issues/2), [booking API](https://github.com/beardedghost45/city-route-booking/issues/3), [React UI](https://github.com/beardedghost45/city-route-booking/issues/4), and [integration checks, CI, and run docs](https://github.com/beardedghost45/city-route-booking/issues/5).

## Acceptance criteria

- [x] The brief names the five supported cities and defines valid routes.
- [x] Required booking fields and the distinction between lead passenger contact and seat capacity are documented.
- [x] Simulated payment and cancellation/refund-pending behavior are documented; real payment and fare calculation are out of scope.
- [x] ETag/If-Match behavior for concurrent updates and idempotency-key behavior for duplicate create retries are documented.
- [x] Booking-number format and uniqueness are documented.
- [x] The brief supports testable acceptance criteria for booking creation, payment status, cancellation, and user-visible confirmation.
- [x] The orchestrator split the approved requirements into ordered database, API, React UI, and testing issues.

Product behavior is resolved. Implementation and technical choices are intentionally outside this requirements issue.
