# Gather MVP booking requirements and acceptance criteria

## Context

We are starting a city-to-city booking application with a Java backend and a React frontend. The initial brief requests five supported cities but names only Delhi, Mumbai, Hyderabad, and Kolkata. Implementation should be split into issues after the product behavior is clear.

## Confirmed decisions

- Payment and cancellation are part of the MVP.
- Each successful booking needs a unique booking number.
- The product owner asked to try an “e tag” for simultaneous booking. The term and expected behavior have not been clarified; do not assume a particular protocol or concurrency rule.

## Requirements questions

- Confirm the fifth city.
- Decide whether every pair of supported cities is valid or only explicitly configured routes are valid.
- Define required booking fields and validation. Travel date, passenger count/details, and contact information are candidate fields, not approved requirements; seat/class selection is also undecided.
- Define booking/payment states and when a booking is confirmed. Select a payment provider or explicitly choose a simulated payment flow for the MVP.
- Define cancellation rules, including whether cancellation is allowed before/after payment and whether cancellation triggers a void, refund, or status change only.
- Clarify “e tag”: does this mean HTTP `ETag`/`If-Match`? Identify the simultaneous operation/conflict it must protect (for example, duplicate submission, two requests competing for the same limited capacity, or concurrent updates/cancellation) and the expected result. Capacity/seat inventory has not been approved as a product requirement.
- Choose the public booking-number format and define uniqueness/collision behavior for concurrent creation. Do not expose a database primary key by default.
- Decide whether authentication or staff/agent tools belong in the MVP. Deferring them is a tentative proposal only.
- Select or delegate the Java framework, database, and React toolchain; record technical decisions separately if the product owner does not need to choose them.

## Acceptance criteria

- [ ] The product brief names exactly five supported cities and documents the route rules.
- [ ] Required booking data and validation behavior are documented.
- [ ] Booking confirmation, payment states/flow, and cancellation behavior (including post-payment outcome) are documented.
- [ ] The meaning and testable concurrency behavior of the requested “e tag” are documented, or the product owner explicitly defers it.
- [ ] Booking-number format and uniqueness behavior under concurrent creation are testable and documented.
- [ ] The brief contains testable acceptance criteria for booking creation, payment, cancellation, and user-visible confirmation.
- [ ] The orchestrator can split the approved requirements into ordered database, API, React UI, and testing issues without relying on unresolved product decisions.

## Smallest clarifications needed before implementation

1. Which payment provider should be used, or should MVP payment be simulated? What should cancellation do after a successful payment (refund/void or no money movement)?
2. By “e tag,” do you mean HTTP `ETag`/`If-Match`, and what specific simultaneous operation should it protect?

Implementation is intentionally not part of this issue; resolve the blocking product decisions first. Previously suggested fields, route rules, booking-number formats, and deferrals remain tentative until approved.
