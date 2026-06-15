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

Confirm you understand:

1. The architecture boundaries.
2. The MVP scope and out-of-scope features.
3. The money calculation rules.
4. The current task order.

Do not modify files yet.
```

## Generic implementation prompt

```md
Read AGENTS.md and TASKS.md first.

Task:
[task id and title]

Goal:
[one precise outcome]

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

## UI task prompt

```md
Read AGENTS.md, android/AGENTS.md, DESIGN_SYSTEM.md, ARCHITECTURE.md, and TASKS.md.

Task:
[UI task id]

Goal:
Implement [screen/component].

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
- Use design system components.
- Do not hardcode colors in feature screens.
- ViewModel calls use cases; no business calculation logic in ViewModel.
- Use immutable UiState and UiEvent.

Validation:
Run:
- ./gradlew :feature:expense-flow:impl:compileDebugKotlin
- ./gradlew assembleDebug

Done when:
- [acceptance criteria]
```

## Review prompt

```md
Review the current diff for architecture and MVP scope compliance.

Check specifically:
1. No feature impl depends on data impl.
2. No domain module imports Android, Compose, DTOs, or database entities.
3. No money calculation uses Float or Double.
4. No out-of-scope features were added.
5. Relevant tests or compile commands were run.
6. Changes are limited to the requested task.

Do not modify files. Return findings grouped by severity.
```

## Subagent review prompt

```md
Spawn read-only review agents if available.

Agent 1:
Inspect architecture boundaries and dependency risks.

Agent 2:
Inspect calculation logic and missing tests.

Agent 3:
Inspect backend API/security issues.

All agents must be read-only and must not modify files.
Return a consolidated action list with severity and suggested fixes.
```
