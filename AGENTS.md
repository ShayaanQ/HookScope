# AGENTS.md

Operational rules for any AI coding agent working in this repository.

This file governs **how** to work. Product scope, architecture, acceptance criteria,
and verification commands live in the linked source-of-truth documents.

## Source of truth

Before doing anything else, read:

- `docs/milestones/M1.md` — Milestone 1 scope, checkpoint boundaries, locked contracts, and non-goals.
- `docs/acceptance/M1.md` — objective acceptance criteria for the active checkpoint.
- `docs/testing.md` — authoritative formatting, analysis, test, build, and smoke-test commands.
- `docs/architecture/` — architecture overview and ADRs once they exist.

If a chat prompt conflicts with these documents, stop and report the conflict. The
repository documents win unless the human explicitly assigns a documentation update.
Do not silently override them in code.

## Before editing

1. Run `git status --short --branch` and report:
   - current branch,
   - staged changes,
   - unstaged changes,
   - untracked files,
   - and anything unexpected.
2. Read the active checkpoint in `docs/milestones/M1.md` and its criteria in
   `docs/acceptance/M1.md`.
3. Restate:
   - the assigned checkpoint,
   - its deliverables,
   - its non-goals,
   - the exact acceptance IDs being targeted,
   - and the files expected to change.
4. Produce this risk table before editing:

| Risk area | Relevant? | Reason | Planned mitigation | Required verification |
|---|---|---|---|---|
| Authentication / authorization | Yes / No |  |  |  |
| Sensitive-data exposure | Yes / No |  |  |  |
| Database integrity | Yes / No |  |  |  |
| Migration safety | Yes / No |  |  |  |
| Concurrency | Yes / No |  |  |  |
| Resource exhaustion | Yes / No |  |  |  |
| Logging / privacy | Yes / No |  |  |  |
| Performance | Yes / No |  |  |  |

Every row must be addressed. “Not relevant” requires a reason.

5. Map each targeted acceptance criterion to:
   - an implementation step,
   - an automated test or manual verification,
   - and the command that will produce the evidence.
6. Stop before editing when:
   - repository state contradicts the milestone,
   - a required decision is still ambiguous,
   - satisfying the checkpoint would violate a non-goal,
   - or unexpected changes may be overwritten.

## Scope discipline

- Work on exactly one assigned checkpoint per session.
- Do not implement later-checkpoint or later-milestone features “for flexibility.”
- Do not modify unrelated code, formatting, configuration, or documentation.
- Do not change milestone scope or acceptance criteria unless explicitly assigned to
  update the source-of-truth documents first.
- Do not delete, weaken, reinterpret, or bypass an acceptance criterion to make a
  checkpoint pass.
- Do not add a dependency without stating why the JDK, Spring Boot, Gradle, or an
  existing dependency is insufficient.
- Do not create placeholder methods, empty tests, stub integrations, fake data paths,
  or TODO-based completion.
- Do not weaken validation, redaction, authentication, body limits, or logging safety
  to make tests pass.
- Keep the repository buildable and testable after each logical change.
- Record architecture changes in an ADR and flag them before implementation.
- Do not invent or substitute verification commands. Use `docs/testing.md` exactly.

## Verification

Run every command applicable to the active checkpoint from `docs/testing.md`.

For every command:

- report the exact command,
- report whether it passed or failed,
- include the relevant output summary,
- and never present an unexecuted command as verified.

Additional requirements:

- Map evidence back to each targeted acceptance ID.
- Confirm integration tests actually discovered and executed tests.
- Include manual smoke commands and observed results where required.
- Inspect application, test, and access logs for leakage of:
  - the admin token,
  - full public endpoint keys,
  - sensitive header values,
  - raw webhook bodies,
  - or full `/hooks/{publicKey}` request paths.
- Never fabricate benchmarks, user counts, test results, build success, or runtime
  behavior.

## Acceptance status ownership

Codex must **not** change any acceptance row to `PASS`.

Codex reports evidence and proposes status changes during handoff. A human reviewer
inspects the evidence and updates `docs/acceptance/M1.md`.

## End-of-session handoff

Before stopping:

1. Run and show `git diff --stat`.
2. Summarize every changed file and why it changed.
3. List all commands executed and their results.
4. Map evidence to every targeted acceptance ID.
5. List every targeted criterion that remains unsatisfied.
6. List known limitations, risks, or follow-up decisions.
7. Show relevant diffs or concise excerpts for security-sensitive and build-system
   changes.
8. Do not commit, push, open a pull request, merge, or start the next checkpoint
   without explicit human approval.

## Escalation

Stop and report instead of guessing when the task cannot be completed without:

- violating a non-goal,
- changing a locked contract,
- overwriting unexpected work,
- weakening security or verification,
- or making an undocumented architecture decision.
