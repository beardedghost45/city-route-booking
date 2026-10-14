# City Route Booking

A Java booking service with a React web client for booking travel between supported Indian cities.

## Product outline

- Create a booking between supported cities.
- Assign each booking a unique, human-readable booking number.
- Start with Delhi, Mumbai, Hyderabad, and Kolkata. The product brief asks for five cities, so the requirements agent must confirm the fifth city before implementation.
- Build and deliver the product through an issue-driven workflow using the five role prompts in [`agents/`](agents/).

## Agent workflow

1. The orchestrator agent turns the product brief into a small, ordered set of GitHub issues.
2. The requirements agent resolves open product questions and records acceptance criteria in the relevant issue.
3. The database agent proposes and implements the persistence model and migrations for an approved issue.
4. The coding agent implements the Java API and React user interface against the agreed requirements.
5. The testing agent adds and runs focused checks for the behavior covered by an issue.
6. The orchestrator checks dependencies, reviews the changes, and closes the loop in the issue.

See [`docs/product-brief.md`](docs/product-brief.md) for the initial scope and open decisions.

## Planned structure

```text
backend/       Java service
frontend/      React application
agents/        Reusable role prompts for the issue-driven workflow
docs/          Product brief and architecture decisions
.github/       Issue templates and GitHub project guidance
```

The application stack and implementation details will be selected in issues after the requirements are confirmed.
