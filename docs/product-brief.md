# Product brief: City Route Booking

## Goal

Create a web application with a Java backend and a React frontend that lets a customer make a travel booking between supported cities.

## Initial capabilities

1. Create a booking for travel between supported cities.
2. Generate and return a unique, opaque booking number with a `BK-` prefix for each booking. Do not expose a database identifier.
3. Simulate payment status in the MVP. Do not connect a real payment provider or move real money. Exact fare calculation is deferred; use a non-financial test amount only if the simulated status flow requires one.
4. Allow cancellation. Cancellation marks the booking cancelled; if payment had succeeded, record a refund-pending status. No real refund is issued.
5. Use an idempotency key on booking creation so retries of the same create request do not create duplicate bookings.
6. Use HTTP `ETag`/`If-Match` to protect concurrent updates to the same booking; reject an update based on a stale tag.
7. Develop through GitHub issues created and sequenced by an orchestrator agent, then worked by specialized agents.

## Confirmed booking requirements

- **Supported cities:** Delhi, Mumbai, Hyderabad, Kolkata, and Bengaluru.
- **Routes:** Every distinct origin/destination pair among the five cities is valid. Origin and destination must differ.
- **Required booking fields:** Travel date, passenger count, lead passenger name, and lead passenger contact. No per-seat capacity or inventory rule is defined.
- **Booking number:** Unique across bookings, opaque, and `BK-` prefixed.
- **Create retries:** A repeated create request with the same idempotency key must not create another booking.
- **Concurrent updates:** Updates to one booking must use `ETag`/`If-Match`; a stale conditional update is rejected.
- **Payment:** Simulated status flow only. No payment gateway, actual charge, or real money movement. Exact fare calculation is deferred unless a non-financial test amount is needed to exercise the simulated status flow.
- **Cancellation:** A cancellation sets the booking to cancelled. After successful simulated payment, it also records refund-pending; no real refund is issued.
- **Deferred from MVP:** Seat/class selection, capacity management, authentication, and staff tools.

## Agent roles

- **Orchestrator:** turns the brief into issues, identifies dependencies, assigns work to role prompts, and tracks completion.
- **Requirements gathering:** asks and records product decisions, defines user flows, and writes testable acceptance criteria.
- **Database:** designs persistence, identifiers, constraints, and migrations in line with approved requirements.
- **Coding:** implements the Java service and React client for an issue.
- **Testing:** derives and runs focused checks for API, UI, and booking-number behavior.

## Implementation choices

- Backend: Java 17 with Spring Boot, PostgreSQL, and Flyway migrations.
- Frontend: React with TypeScript and Vite.
- Idempotency keys are global in this unauthenticated MVP and stay with a booking. A retry with the same normalized request returns the original booking; reuse with a different request returns HTTP 409.
- New bookings start pending payment. A demo action records simulated success or failure.
- Every state-changing request for an existing booking requires `If-Match`; a missing header returns HTTP 428 and a stale tag returns HTTP 412.
- GitHub Actions runs backend and frontend checks.

The work is split into ordered GitHub issues: [database foundation](https://github.com/beardedghost45/city-route-booking/issues/2), [booking API](https://github.com/beardedghost45/city-route-booking/issues/3), [React UI](https://github.com/beardedghost45/city-route-booking/issues/4), and [integration checks and run docs](https://github.com/beardedghost45/city-route-booking/issues/5).
