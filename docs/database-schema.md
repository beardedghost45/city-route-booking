# Booking persistence decisions

Issue #2 implements the PostgreSQL persistence foundation using Spring Data JPA and Flyway. The first migration is `backend/src/main/resources/db/migration/V1__create_bookings.sql`.

## Booking row

`bookings.id` is an internal UUID primary key and is not the public identifier. Each row also has a randomly generated, opaque `booking_number` in the form `BK-` plus 32 lowercase hexadecimal characters. Both booking number and idempotency key have database unique constraints.

The row stores origin, destination, travel date, passenger count, lead passenger name and contact, booking status, payment status, refund status, the idempotency key, a create-request fingerprint, a JPA-managed version, and timestamps. Lead passenger name and contact are trimmed at the domain boundary. No payment credentials or real transaction amounts are stored.

The allowed city values are `DELHI`, `MUMBAI`, `HYDERABAD`, `KOLKATA`, and `BENGALURU`. Database checks require each route to use two different supported cities and require a positive passenger count. There is no capacity or inventory column or rule.

## Idempotency

The idempotency key is stored on its booking row, is globally unique for this unauthenticated MVP, and remains for the lifetime of the booking. `request_fingerprint` is the lowercase hexadecimal SHA-256 digest of normalized create fields in this fixed order: origin enum name, destination enum name, ISO-8601 travel date, decimal passenger count, trimmed lead passenger name, trimmed lead passenger contact. Each UTF-8 field is prefixed by a four-byte big-endian byte length; the first field is the format marker `city-route-booking:create:v1`. Contact values are trimmed but not case-folded. The API should call `BookingRequestFingerprint.calculate` so retries are compared using the same representation.

Run `./mvnw test` from `backend/` to execute the domain and PostgreSQL persistence suites. The persistence suite starts a disposable PostgreSQL 18 container and is automatically skipped when Docker is unavailable.

## Lifecycle and optimistic version

Bookings start as `PENDING_PAYMENT` with payment `PENDING` and refund `NONE`. A successful simulated payment moves the booking to `CONFIRMED` / `SUCCEEDED`; a failed attempt leaves the booking pending and records `FAILED`. Cancelling an unpaid booking sets `CANCELLED` and leaves refund `NONE`; cancelling a paid booking sets `CANCELLED` and refund `PENDING`. The database constrains allowed states and their consistency.

The `version` column is a JPA `@Version` field. Updates increment it optimistically and can back a strong HTTP ETag in the API issue. A stale update results in the persistence layer's optimistic-lock failure; HTTP mapping is left to issue #3.
