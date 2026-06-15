# EvenUp Codex Starter Files

## What this pack contains

Copy the contents of this folder into the root of your EvenUp repository.

The pack includes persistent Codex instructions, architecture rules, product scope, API contracts, calculation rules, design system guidance, task breakdown, prompt templates, and the generated Stitch design reference.

## Files

```text
AGENTS.md
TASKS.md
ARCHITECTURE.md
MVP_SCOPE.md
CALCULATIONS.md
API_CONTRACT.md
DESIGN_SYSTEM.md
android/AGENTS.md
backend/AGENTS.md
prompts/CODEX_PROMPTS.md
docs/design/STITCH_REFERENCE.md
docs/design/original_refs/functionality_flow_reference.png
docs/design/original_refs/premium_ui_reference.png
docs/design/stitch/**
```

## How to paste into the project

From your repo root, copy all files and folders into the root directory.

Expected result:

```text
<repo-root>/AGENTS.md
<repo-root>/TASKS.md
<repo-root>/ARCHITECTURE.md
<repo-root>/MVP_SCOPE.md
<repo-root>/CALCULATIONS.md
<repo-root>/API_CONTRACT.md
<repo-root>/DESIGN_SYSTEM.md
<repo-root>/android/AGENTS.md
<repo-root>/backend/AGENTS.md
<repo-root>/prompts/CODEX_PROMPTS.md
<repo-root>/docs/design/STITCH_REFERENCE.md
<repo-root>/docs/design/stitch/...
```

If your repo does not yet have `android/` or `backend/` folders, keep these instruction folders anyway. Codex can use them as target directory guidance.

## First Codex prompt

Use the Start prompt from:

```text
prompts/CODEX_PROMPTS.md
```

Or paste this:

```md
Read these files first:

- AGENTS.md
- TASKS.md
- ARCHITECTURE.md
- MVP_SCOPE.md
- CALCULATIONS.md
- API_CONTRACT.md
- DESIGN_SYSTEM.md
- docs/design/STITCH_REFERENCE.md

Confirm you understand:

1. The architecture boundaries.
2. The MVP scope and out-of-scope features.
3. The money calculation rules.
4. The design source of truth and Stitch reference usage.
5. The current task order.

Do not modify files yet.
```

Then ask Codex to start with `T001` or `T002`, depending on whether your Android project already exists.

## Important Codex workflow

Do not give Codex the whole MVP as one task.

Use `TASKS.md` and execute one task at a time.

Good:

```md
Read AGENTS.md and TASKS.md.
Start T010 only.
Do not proceed to T011.
```

Bad:

```md
Build the whole EvenUp MVP.
```

## Design usage

The Stitch export is stored under:

```text
docs/design/stitch/
```

Use it as persistent repo context. For screen tasks, reference only the relevant folder.

Example:

```md
Task: T070 - Implement New Expense screen.
Use:
- docs/design/stitch/new_expense/screen.png
- docs/design/stitch/new_expense/code.html
```

The Stitch HTML is not production code. Codex should implement native Compose using the design system.

## Known Stitch export issue

`docs/design/stitch/receipt_scan/screen.png` may be invalid and may contain `FIFE Image failed to fetch`.

For the Receipt Scan screen, use:

- `docs/design/stitch/receipt_scan/code.html`
- `DESIGN_SYSTEM.md`
- `docs/design/original_refs/`

## Recommended first build sequence

1. T000 - Add instruction/design files.
2. T001 - Create or verify Android project baseline.
3. T002 - Create Gradle module skeleton.
4. T003 - Wire baseline dependency graph.
5. T005 - Implement design tokens and app theme.
6. T010 to T018 - Domain and calculation engine.
7. T050 to T061 - Backend save/fetch/guest page.
8. T060 to T064 - Android data layer.
9. T070 onward - Manual app flow.
10. T110 onward - Scanner integration.
11. T120 onward - Demo hardening.

Manual full flow should work before receipt scanner integration.
