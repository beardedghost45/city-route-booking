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

## Technical follow-up

- Choose or delegate the Java framework, database, and React toolchain in implementation issues.
- Define the idempotency-key scope/retention and the API response for a conflicting key payload.
- Define the precise stale-`If-Match` API response and persistence implementation.
- Split database, API, React UI, and focused test work into dependent issues based on the confirmed behavior above.

## Acceptance criteria

- [x] The brief names the five supported cities and defines valid routes.
- [x] Required booking fields and the distinction between lead passenger contact and seat capacity are documented.
- [x] Simulated payment and cancellation/refund-pending behavior are documented; real payment and fare calculation are out of scope.
- [x] ETag/If-Match behavior for concurrent updates and idempotency-key behavior for duplicate create retries are documented.
- [x] Booking-number format and uniqueness are documented.
- [x] The brief supports testable acceptance criteria for booking creation, payment status, cancellation, and user-visible confirmation.
- [ ] The orchestrator can split the approved requirements into ordered database, API, React UI, and testing issues without relying on unresolved product decisions.

Product behavior is resolved. Implementation and technical choices are intentionally outside this requirements issue.
