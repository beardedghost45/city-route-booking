# City Route Booking

A Java booking service with a React web client for booking travel between supported Indian cities.

## Product outline

- Create a booking between supported cities.
- Assign each booking a unique opaque booking number prefixed with `BK-`.
- Support Delhi, Mumbai, Hyderabad, Kolkata, and Bengaluru. Any two distinct cities form a valid route.
- Include simulated payment and cancellation; a paid cancellation records refund-pending without moving real money.
- Protect duplicate create retries with an idempotency key and concurrent booking changes with HTTP ETag/If-Match.
- Provide a read-only admin bookings page at `/admin`, protected by a configured admin username and password.
- Build and deliver the product through an issue-driven workflow using the five role prompts in [`agents/`](agents/).

## Agent workflow

1. The orchestrator agent turns the product brief into a small, ordered set of GitHub issues.
2. The requirements agent resolves open product questions and records acceptance criteria in the relevant issue.
3. The database agent proposes and implements the persistence model and migrations for an approved issue.
4. The coding agent implements the Java API and React user interface against the agreed requirements.
5. The testing agent adds and runs focused checks for the behavior covered by an issue.
6. The orchestrator checks dependencies, reviews the changes, and closes the loop in the issue.

See [`docs/product-brief.md`](docs/product-brief.md) for the approved scope. Implementation is tracked in [GitHub issues](https://github.com/beardedghost45/city-route-booking/issues), including the [admin dashboard issue #7](https://github.com/beardedghost45/city-route-booking/issues/7).

## Planned structure

```text
backend/       Java service
frontend/      React application
agents/        Reusable role prompts for the issue-driven workflow
docs/          Product brief and architecture decisions
.github/       Issue templates and GitHub project guidance
```

The selected stack is Java 17 with Spring Boot, PostgreSQL and Flyway, plus React, TypeScript, and Vite.

## Run locally

Prerequisites: Java 17, Node.js 24 with npm, and Docker Compose v2.

1. Start PostgreSQL 18 with the repository defaults:

   ```sh
   docker compose up -d postgres
   ```

   The database is available at `localhost:5432` as `city_route_booking`, with username and password `booking`. The API applies Flyway migrations at startup.

2. In a terminal, start the API:

   ```sh
   cd backend
   ADMIN_USERNAME=booking-admin \
   ADMIN_PASSWORD='replace-with-a-local-secret' \
   ./mvnw spring-boot:run
   ```

   The API listens on `http://localhost:8080`. Its default database connection matches the Compose service. Override `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD` to use another PostgreSQL instance. The default browser origin is `http://localhost:5173`; set `FRONTEND_ORIGINS` to a comma-separated list of exact frontend origins when changing it. The admin endpoint has no built-in credentials, so set both variables to non-blank values to enable admin sign-in. Keep the password out of source control.

3. In another terminal, install the locked frontend dependencies and start Vite:

   ```sh
   cd frontend
   npm ci
   npm run dev
   ```

   Open `http://localhost:5173` for booking or `http://localhost:5173/admin` to review bookings. Vite proxies `/api` requests to the local API. To use a separately hosted API, copy `frontend/.env.example` to `frontend/.env` and set `VITE_API_BASE_URL`.

## Run checks

From the repository root:

```sh
cd backend
./mvnw --batch-mode test
cd ../frontend
npm ci
npm run typecheck
npm test
npm run build
```

The backend API and persistence acceptance tests use PostgreSQL 18 through Testcontainers and apply the Flyway migration. Docker must be running for those suites; Testcontainers marks them skipped when Docker is unavailable. GitHub Actions runs them on hosted Ubuntu, which provides Docker. The frontend checks, deterministic UI tests, and production build do not require a running API.

To stop the local database, run `docker compose down` from the repository root.

## Booking behavior and MVP limits

Bookings use any two distinct cities among Delhi, Mumbai, Hyderabad, Kolkata, and Bengaluru. A booking requires a travel date, positive passenger count, lead passenger name, and contact. Create requests require an `Idempotency-Key`; repeating the same key and payload returns the original booking. Payment and cancellation updates require the current `ETag` in `If-Match`; stale updates return `412`, and missing tags return `428`.

Payment and refund states are simulations only: the application does not connect to a payment provider, charge or refund money, or calculate fares. The MVP has no seat-capacity management or customer authentication. The read-only admin bookings API uses HTTP Basic credentials from `ADMIN_USERNAME` and `ADMIN_PASSWORD`; configure both before enabling admin access. See [`docs/api-contract.md`](docs/api-contract.md) for request, response, and error details.
