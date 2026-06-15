# Codex Prompt Templates for EvenUp

Use these prompts one task at a time.

## Start prompt

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

## Generic implementation prompt

```md
Read AGENTS.md and TASKS.md first.

Task:
[task id and title]

Goal:
[one precise outcome]

Context:
Relevant files:
- [file]

Scope:
You may edit:
- [file or module]

Do not edit:
- [file or module]

Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

Implementation requirements:
- [specific requirements]

Validation:
Run:
- [specific command]

Done when:
- [acceptance criteria]

Do not continue to the next task.
```

## Plan-first prompt for risky tasks

```md
Use plan mode first.

Task:
[task id and title]

Before coding:
1. Inspect the relevant files.
2. Identify files/modules that need changes.
3. Propose a short implementation plan.
4. List validation commands.
5. Wait for my approval before editing files.

Constraints:
- Do not edit unrelated files.
- Do not introduce new dependencies without approval.
- Preserve architecture boundaries.
```

## Domain task prompt

```md
Read AGENTS.md, ARCHITECTURE.md, CALCULATIONS.md, and TASKS.md.

Task:
[domain task id]

Goal:
Implement only the domain logic for [feature].

Scope:
You may edit:
- :domain:expense:api
- :domain:expense:impl
- :domain:receipt:api if needed
- :domain:participant:api if needed

Do not edit:
- feature modules
- data modules
- app module
- backend
- docs/design

Rules:
- No Android imports.
- No Compose.
- No DTOs.
- No Float or Double for money.
- Use integer minor units.
- Use deterministic rounding.

Tests required:
- [explicit tests]

Validation:
Run:
- ./gradlew :domain:expense:impl:test

Done when:
- Tests pass.
- Domain API remains clean.
```

## Backend task prompt

```md
Read AGENTS.md, backend/AGENTS.md, API_CONTRACT.md, and TASKS.md.

Task:
[backend task id]

Goal:
Implement [endpoint or backend feature].

Scope:
You may edit:
- backend files only

Do not edit:
- Android files
- domain files

Requirements:
- Follow API_CONTRACT.md.
- Return safe errors.
- Do not log receipt image payloads.
- Do not expose OpenAI API key.

Validation:
Run:
- [backend command]

Done when:
- [acceptance criteria]
```

## Backend guest web prompt

```md
Read AGENTS.md, backend/AGENTS.md, API_CONTRACT.md, DESIGN_SYSTEM.md, and docs/design/STITCH_REFERENCE.md.

Task:
Implement the guest web view for GET /e/:shareId.

Use these design references:
- docs/design/stitch/guest_view_web/screen.png
- docs/design/stitch/guest_view_web/code.html
- docs/design/stitch/validation_states/screen.png for error states

Scope:
You may edit:
- backend files only

Do not edit:
- Android files
- domain files

Requirements:
- Public and read-only.
- No login.
- No payment UI.
- Mobile-first.
- White/black premium style.
- Show settlement summary and transparent breakdown.
- Safe 404/error states.

Validation:
Run:
- [backend command]

Done when:
- A saved share ID opens a readable mobile web page.
- Missing share ID returns a safe styled error page.
```

## UI task prompt

```md
Read AGENTS.md, android/AGENTS.md, DESIGN_SYSTEM.md, docs/design/STITCH_REFERENCE.md, ARCHITECTURE.md, and TASKS.md.

Task:
[UI task id]

Goal:
Implement [screen/component].

Use these design references:
- docs/design/stitch/[screen_folder]/screen.png
- docs/design/stitch/[screen_folder]/code.html

Scope:
You may edit:
- :feature:expense-flow:impl
- :feature:expense-flow:api if route contract is needed
- :core:designsystem:* only if reusable component is missing

Do not edit:
- domain calculation logic unless explicitly required
- data implementation modules
- backend

Rules:
- Do not copy Stitch HTML directly.
- Implement native Jetpack Compose.
- Use design system components and tokens.
- Do not hardcode colors in feature screens.
- ViewModel calls use cases; no business calculation logic in ViewModel.
- Use immutable UiState and UiEvent.
- Do not add out-of-scope UI such as groups, friends, payments, history, or AI text input.

Validation:
Run:
- ./gradlew :feature:expense-flow:impl:compileDebugKotlin
- ./gradlew assembleDebug

Done when:
- The screen matches the reference visually and behaviorally within native Android constraints.
- The relevant compile command passes.
```

## Design system task prompt

```md
Read AGENTS.md, android/AGENTS.md, DESIGN_SYSTEM.md, docs/design/STITCH_REFERENCE.md, and TASKS.md.

Task:
[design system task id]

Goal:
Implement reusable design system components for EvenUp.

Context:
Use these references:
- docs/design/stitch/new_expense/screen.png
- docs/design/stitch/choose_people/screen.png
- docs/design/stitch/assign_items/screen.png
- docs/design/stitch/review_expense/screen.png
- docs/design/stitch/expense_saved/screen.png

Scope:
You may edit:
- :core:designsystem:api
- :core:designsystem:impl

Do not edit:
- feature screens unless explicitly requested
- domain modules
- data modules
- backend

Rules:
- Expose semantic components and tokens.
- Do not tie reusable components to one screen's business model.
- Keep styling consistent with white/black premium fintech direction.

Validation:
Run:
- ./gradlew :core:designsystem:impl:compileDebugKotlin
- ./gradlew assembleDebug

Done when:
- Components compile.
- Feature screens can consume components via design system API.
```

## Stitch-informed screen prompt examples

### New Expense screen

```md
Read AGENTS.md, android/AGENTS.md, DESIGN_SYSTEM.md, docs/design/STITCH_REFERENCE.md, and TASKS.md.

Task:
T070 - Implement New Expense screen.

Use these design references:
- docs/design/stitch/new_expense/screen.png
- docs/design/stitch/new_expense/code.html

Goal:
Implement the EvenUp entry screen in native Compose.

Scope:
You may edit:
- :feature:expense-flow:api
- :feature:expense-flow:impl
- :core:designsystem:* only if a required reusable component is missing

Do not edit:
- domain modules
- data modules
- backend

Requirements:
- App name EvenUp.
- Primary CTA: Scan receipt.
- Secondary CTA: Enter manually.
- No groups, friends, history, or AI text input.
- Use design system tokens/components.

Validation:
Run:
- ./gradlew :feature:expense-flow:impl:compileDebugKotlin
```

### Assign Items screen

```md
Read AGENTS.md, android/AGENTS.md, DESIGN_SYSTEM.md, docs/design/STITCH_REFERENCE.md, CALCULATIONS.md, and TASKS.md.

Task:
T090 - Implement Assign Items screen skeleton.

Use these design references:
- docs/design/stitch/assign_items/screen.png
- docs/design/stitch/assign_items/code.html
- docs/design/stitch/item_split/screen.png for future bottom-sheet behavior context

Goal:
Implement participant selector, receipt item rows, assignment progress, and disabled Continue CTA.

Scope:
You may edit:
- :feature:expense-flow:impl
- :core:designsystem:* only if receipt row/avatar components are missing

Do not edit:
- domain calculation logic
- data implementation modules
- backend

Rules:
- Main interaction is tap person, then tap items.
- Do not put calculation logic in ViewModel.
- Use domain use cases for validation/assignment state when available.

Validation:
Run:
- ./gradlew :feature:expense-flow:impl:compileDebugKotlin
```

## Review prompt

```md
Review the current diff for architecture, MVP scope, and design compliance.

Check specifically:
1. No feature impl depends on data impl.
2. No domain module imports Android, Compose, DTOs, or database entities.
3. No money calculation uses Float or Double.
4. No out-of-scope features were added.
5. Feature screens use design system components/tokens.
6. UI implementation follows relevant docs/design/stitch references.
7. Relevant tests or compile commands were run.
8. Changes are limited to the requested task.

Do not modify files. Return findings grouped by severity.
```

## Subagent review prompt

```md
Spawn read-only review agents if available.

Agent 1:
Review Android architecture boundaries.

Agent 2:
Review domain calculation and money rules.

Agent 3:
Review UI/design implementation against DESIGN_SYSTEM.md and docs/design/STITCH_REFERENCE.md.

Agent 4:
Review backend API/security if backend changed.

Do not modify files.
Return a consolidated findings list grouped by severity.
```
