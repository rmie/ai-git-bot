# Harden context-window management: fix bugs, remove duplication, add tests

## Problem

The context-window management subsystem — responsible for keeping the
agent's conversation history within the AI model's token limits — had
several issues:

1. **Premature and permanent proactive compaction.** The `TokenUsageTracker`
   used cumulative `totalInputTokens` to decide whether to compact. Since
   each AI call's prompt already includes the full history, cumulative
   tokens grow superlinearly (round 1 = 5k, round 2 = 15k, round 3 = 30k
   → cumulative = 50k, but the real prompt at round 3 is only 30k). This
   caused compaction to trigger far too early and never stop.

2. **Crash on small truncation budgets.** `ToolResultTruncator` computed
   `tailSize = maxChars - headSize - 64` without guarding against a
   negative result. With small `maxChars` values, this threw
   `StringIndexOutOfBoundsException` at runtime.

3. **Duplicated `isPromptTooLongError` logic.** Both `AgentLoop` and
   `E2eAgentRunner` contained identical private copies of the same
   7-line error-detection method, creating a maintenance hazard.

4. **Hardcoded budget defaults in a domain record.** `AgentBudget` held
   four `DEFAULT_` constants that duplicated values from the config
   layer, making the source of truth ambiguous.

5. **No tests for critical new logic.** The branch-heavy code introduced
   by ADR-1 (tool-pair grouping, cut-point walking, truncation
   arithmetic, threshold math) shipped without any unit tests.

## Changes

### Bug fixes

- **`TokenUsageTracker` now tracks `lastInputTokens`** (transient field)
  instead of using the cumulative `totalInputTokens` counter. The
  `shouldCompactProactively()` and `usageFraction()` methods now compare
  the most recent AI call's input tokens against the context window —
  the correct measure of current occupancy. Cumulative counters on
  `AgentSession` remain for cost/audit purposes only.

- **`ToolResultTruncator` guards against negative `tailSize`.** When
  `maxChars` is too small to fit both head and tail plus the marker,
  the truncator now falls back to head-only mode with the marker,
  instead of crashing.

### Refactoring

- **`isPromptTooLongError` moved to `AiClient` interface** as a default
  method with a superset heuristic (checks HTTP status codes 400/413/429/500
  and keyword patterns in exception messages). All five provider clients
  retain their `public` overrides for provider-specific patterns.
  `AgentLoop` and `E2eAgentRunner` now delegate to `aiClient.isPromptTooLongError()`,
  eliminating the duplication.

- **Removed all `DEFAULT_` constants from `AgentBudget`.** The record is
  now a pure value holder. All defaults are sourced from
  `AgentConfigProperties.BudgetConfig` (which gained a
  `proactiveCompactionThreshold` property, default 0.7). Service classes
  that previously referenced `AgentBudget.DEFAULT_*` now read from
  `budgetCfg` directly.

### Testing

Added 43 unit tests across three new test classes:

| Test class | Tests | What it covers |
|---|---|---|
| `HistoryCompactorTest` | 16 | Tool-pair atomic grouping (ADR-1 invariant), aggressive compaction, no-op guards, orphan handling, `messageChars` utility |
| `ToolResultTruncatorTest` | 12 | Head+tail arithmetic, small-budget guard, head/tail preservation, marker accuracy, 100K input boundary |
| `TokenUsageTrackerTest` | 15 | Last-call tracking, threshold triggering, no re-fire after compaction, cumulative counters still tracked, edge cases (null session, zero context window) |

### Documentation

Added `docs/context-window-management.md` — a developer-facing
architectural document explaining the four-layer context management
strategy (tool result truncation, proactive compaction, aggressive
compaction, reactive error handling) using pseudo-code and ASCII
diagrams, with no concrete class or package references.

## Verification

- Full test suite: **971 tests passing** (928 original + 43 new), zero
  failures, zero errors.
- Compile: clean, no warnings in modified files.
