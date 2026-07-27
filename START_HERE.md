# HookScope Codex Pack

## Files

Place these files into the HookScope repository at the same paths:

```text
AGENTS.md
docs/milestones/M1.md
docs/acceptance/M1.md
docs/testing.md
```

Then open Codex in the repository root and paste the contents of:

```text
CODEX_M1-A_PROMPT.md
```

## Review sequence

1. Codex performs M1-A pre-edit analysis and implementation.
2. Codex runs local verification and Compose smoke tests.
3. Codex stops without committing or pushing.
4. Bring its full handoff output and repository diff back for human/AI review.
5. Only after approval should M1-A be committed and M1-B planned.

Do not start M1-B merely because M1-A code appears to work. Acceptance statuses remain
human-owned.
