# Repository agent guidance

Use GitHub issues as the source of work. Before changing application behavior, read the issue and its acceptance criteria. Keep each change scoped to one issue and report the issue number in the completion summary.

The role prompts in `agents/` describe the responsibilities for this project:

- `orchestrator.md` coordinates issue creation, sequencing, and integration.
- `requirements.md` resolves product questions and writes acceptance criteria.
- `database.md` owns schema and persistence decisions.
- `coding.md` implements the Java API and React client.
- `testing.md` validates behavior and adds focused tests.

The issue workflow starts with requirements. Do not guess the fifth city: the brief currently names only Delhi, Mumbai, Hyderabad, and Kolkata. Record the decision in the requirements issue before implementing route validation.

Do not claim a role prompt has run unless an agent was actually invoked. Do not close an issue until its acceptance criteria have been checked and the relevant changes are summarized.
