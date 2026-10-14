# Product brief: City Route Booking

## Goal

Create a web application with a Java backend and a React frontend that lets a customer make a travel booking between supported cities.

## Initial capabilities

1. Create a booking by selecting an origin and destination.
2. Generate and return a unique booking number for each successful booking.
3. Support an initial network of five cities. The user named Delhi, Mumbai, Hyderabad, and Kolkata; the fifth city is undecided.
4. Develop through GitHub issues created and sequenced by an orchestrator agent, then worked by specialized agents.

## Agent roles

- **Orchestrator:** turns the brief into issues, identifies dependencies, assigns work to role prompts, and tracks completion.
- **Requirements gathering:** asks and records product decisions, defines user flows, and writes testable acceptance criteria.
- **Database:** designs persistence, identifiers, constraints, and migrations in line with approved requirements.
- **Coding:** implements the Java service and React client for an issue.
- **Testing:** derives and runs focused checks for API, UI, and booking-number behavior.

## Open questions for the requirements issue

- What is the fifth city?
- Are bookings for travel between every pair of supported cities, or only configured routes?
- Which booking details are required (travel date, passenger count, passenger names, contact details, seat/class selection)?
- Is a booking merely created, or should it also have payment and cancellation states?
- Should the booking number be numeric, prefixed, or otherwise formatted? It must be unique and must not reveal sequential database IDs unless explicitly desired.
- Should this first version include authentication and an admin/agent workflow?
- Which Java framework, database, and React toolchain should the project use?

## Initial non-goal

Do not implement payment processing or real transport inventory until the requirements issue explicitly includes them.
