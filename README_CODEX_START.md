# EvenUp Codex Starter Pack

This folder contains the instruction files and execution board for building the EvenUp MVP with Codex.

Recommended placement in the repository:

```text
repo-root/
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
```

Recommended Codex workflow:

1. Add these files to the repo root.
2. Open Codex in the repository.
3. Ask Codex to read `AGENTS.md`, `TASKS.md`, `ARCHITECTURE.md`, `MVP_SCOPE.md`, `CALCULATIONS.md`, `API_CONTRACT.md`, and `DESIGN_SYSTEM.md`.
4. Ask Codex to execute exactly one task from `TASKS.md`.
5. Review the diff.
6. Run the validation command.
7. Continue to the next task only after the current task builds/tests.

Do not give Codex the full MVP as one task. Use the prompts in `prompts/CODEX_PROMPTS.md`.
