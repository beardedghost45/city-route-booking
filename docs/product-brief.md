# Product brief: City Route Booking

## Goal

Create a web application with a Java backend and a React frontend that lets a customer make a travel booking between supported cities.

## Initial capabilities

1. Create a booking by selecting an origin and destination.
2. Generate and return a unique booking number for each successful booking.
3. Support an initial network of five cities. The user named Delhi, Mumbai, Hyderabad, and Kolkata; the fifth city is undecided.
4. Include payment and cancellation in the MVP. Their exact flows and rules remain undecided.
5. The product owner requested trying an “e tag” for simultaneous booking. Its meaning and required concurrency behavior remain undecided; do not assume HTTP ETag semantics.
6. Develop through GitHub issues created and sequenced by an orchestrator agent, then worked by specialized agents.

## Agent roles

- **Orchestrator:** turns the brief into issues, identifies dependencies, assigns work to role prompts, and tracks completion.
- **Requirements gathering:** asks and records product decisions, defines user flows, and writes testable acceptance criteria.
- **Database:** designs persistence, identifiers, constraints, and migrations in line with approved requirements.
- **Coding:** implements the Java service and React client for an issue.
- **Testing:** derives and runs focused checks for API, UI, and booking-number behavior.

## Confirmed product decisions

- Payment and cancellation belong in the MVP.
- Each successful booking needs a unique booking number.
- Five cities are intended, but only Delhi, Mumbai, Hyderabad, and Kolkata have been named.
- The product owner asked to try an “e tag” for simultaneous booking; this records the request, not a chosen technical mechanism or behavior.

## Unresolved requirements

- Name the fifth city.
- Decide whether all distinct city pairs are valid or only configured routes.
- Specify required booking fields and validation (for example, travel date, passenger count and details, and contact information). Seat/class selection is still undecided.
- Define the booking lifecycle, payment flow/provider, and cancellation outcome, including what happens to a payment when a booking is cancelled.
- Clarify “e tag”: does this mean HTTP `ETag`/`If-Match`? Which simultaneous operation or conflict must it protect, and what outcome should a conflicting request receive? No transport inventory or capacity rules have been approved, so overbooking behavior cannot be inferred.
- Choose a public booking-number format and define collision behavior under concurrent requests. Do not expose a sequential database ID by default.
- Decide whether authentication or staff/agent tools belong in the MVP. Deferring them is only a proposal, not an approved decision.
- Select or delegate the Java framework, database, and React toolchain; these are technical choices, not confirmed product requirements.

## Tentative ideas, not approved

- Using every distinct city pair as a valid route.
- Requiring travel date, passenger count/details, and contact information.
- Deferring authentication, staff tools, and seat/class selection.
- Using an opaque prefixed booking number.

## Scope boundary

Payment and cancellation are in scope, but implementation details must wait for the unresolved requirements above. Real transport inventory and capacity management have not been requested or approved.
