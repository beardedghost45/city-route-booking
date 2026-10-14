# Gather MVP booking requirements and acceptance criteria

## Context

We are starting a city-to-city booking application with a Java backend and a React frontend. The initial brief requests five supported cities but names only Delhi, Mumbai, Hyderabad, and Kolkata. Implementation should be split into issues after the product behavior is clear.

## Requirements questions

- Confirm the fifth city.
- Decide whether every pair of supported cities is valid or only explicitly configured routes are valid.
- Define the booking form fields, including travel date, passenger count, passenger details, and any seat/class selection needed for the MVP.
- Define when a booking is considered created and whether the first release needs payment, cancellation, authentication, or staff tools.
- Specify the public booking-number format and uniqueness requirements, including behavior when two bookings are created at nearly the same time. Do not expose a database primary key as the public booking number by default.
- Confirm the initial Java framework, database, and React toolchain, or record a decision task for the orchestrator.

## Acceptance criteria

- [ ] The product brief names exactly five supported cities and documents the route rules.
- [ ] Required booking data and validation behavior are documented.
- [ ] The booking lifecycle and first-release exclusions are documented.
- [ ] Booking-number format and uniqueness behavior are testable and documented.
- [ ] The brief contains testable acceptance criteria for booking creation and the user-visible booking confirmation.
- [ ] The orchestrator can split the approved requirements into ordered database, API, React UI, and testing issues without relying on unresolved product decisions.
