# Context Window Management — Implementation Plan

## Problem

The agentic workflows (`CodingAgent`, `WriterAgent`, `ReviewAgent`, E2E agents) have **no
context window management**. Conversation history grows monotonically until the model's
context limit is hit, causing provider errors like `"prompt too long"` or degraded output
quality. Only the classic non-agentic review path (`SessionService.compactContextWindow()`)
implements any form of compaction.

### Root Causes

1. **Unbounded in-memory history** — `AgentLoop.history` is an `ArrayList<AiMessage>` that
   grows every round (user + assistant + N tool results). No pruning, no sliding window.
2. **Unbounded tool results** — Tool outputs (build logs, file contents, tree listings)
   are added to history verbatim. A single `cat` on a large file can inject 100k+ chars.
3. **No token tracking** — `AgentBudget` only tracks round counts and tool execution caps.
   There is no cumulative token usage counter, no "remaining budget" signal.
4. **No graceful degradation** — When a provider returns "prompt too long", the loop
   crashes. No retry-with-compaction logic exists.
5. **No session-level compaction** — `AgentSessionService` never compacts persisted
   messages. On resume (follow-up comments on the same issue), the full history is
   replayed, immediately exhausting context on the first round.

### Affected Code Paths

| Component | File | Problem |
|-----------|------|---------|
| Main agent loop | `agent/loop/AgentLoop.java` | `history` grows unbounded per run |
| Agent session service | `agent/session/AgentSessionService.java` | No `compactContextWindow()` |
| Agent budget | `agent/loop/AgentBudget.java` | No token usage tracking |
| E2E runner | `prworkflow/e2e/agents/E2eAgentRunner.java` | `history` grows unbounded (same pattern) |
| Tool execution | `agent/tools/AgentToolRouter.java` | No result-size cap |
| Abstract AI client | `ai/AbstractAiClient.java` | `isPromptTooLongError()` exists but is unused by `AgentLoop` |

---

## ADR-1: Context Compaction Strategy for Agentic Workflows

**Status:** Proposed

**Context**
The classic `SessionService` uses a simple "drop old messages, keep last 4" strategy.
Agentic workflows differ in that they contain `role:"tool"` messages with `toolCallId`
correlations that must remain consistent with the preceding assistant `tool_calls` block.
Dropping an assistant turn but keeping its tool results — or vice versa — causes hard
provider errors. The compaction strategy must respect this pairing.

**Options Considered**

1. **Port `SessionService.compactContextWindow()` verbatim**
   - ✅ Simple, proven pattern
   - ❌ Ignores tool-message pairing; causes "orphan tool message" errors on replay
   - ❌ Only triggers on persisted session, not in-memory during a run

2. **Sliding window with tool-pair-aware compaction**
   - ✅ Preserves tool/assistant correlation
   - ✅ Works both in-memory (during a run) and on persisted sessions (on resume)
   - ❌ More complex to implement
   - ❌ Requires understanding of `AiMessage` tool-call structure

3. **Summarization via LLM call**
   - ✅ Highest quality context preservation
   - ❌ Adds latency (extra LLM call)
   - ❌ Adds cost (tokens for summarization)
   - ❌ Circular dependency: needs context window to summarize context window

**Decision**
We choose **Option 2 (sliding window with tool-pair-aware compaction)** as the primary
strategy, with an optional **Option 3 (LLM summarization)** as a future enhancement
behind a config flag. The sliding window operates on **compaction units** — each unit
is either a single user/assistant message, or an assistant+tool-result group that is
kept or dropped atomically.

**Consequences**
- History never exceeds a configurable character budget
- Tool-message correlation is always preserved
- No extra LLM calls needed for basic compaction
- The in-memory `AgentLoop.history` and persisted `AgentSessionService` both get compaction

---

## Implementation Steps

### Step 1: Port `compactContextWindow()` to `AgentSessionService`

**Goal:** Add session-level compaction that runs when a session is resumed (e.g., follow-up
comment on the same issue). Prevents history bloat from consuming the entire context on the
first round of a resumed session.

**Components to create / modify:**
- [ ] Modify: `AgentSessionService` — add `compactContextWindow(AgentSession)` method
- [ ] Modify: `AgentConfigProperties.ContextConfig` — add `agentCompactionThresholdChars` and `agentCompactionKeepMessages`

**Sequence:**

1. Add config properties to `AgentConfigProperties.ContextConfig`:
   ```java
   /**
    * Total character threshold that triggers compaction of persisted
    * agent session history. Default: 80_000 (higher than classic review's
    * 50k because agent sessions legitimately carry more context).
    */
   private int agentCompactionThresholdChars = 80_000;

   /**
    * Number of most recent conversation messages to keep after compaction.
    * Tool-pair-aware: the actual count may be slightly higher to avoid
    * splitting an assistant+tool group. Default: 8.
    */
   private int agentCompactionKeepMessages = 8;
   ```

2. Add `compactContextWindow(AgentSession session)` to `AgentSessionService`.
   The method:
   - Calculates total character count across all persisted `ConversationMessage`s.
   - If below threshold, returns unchanged.
   - Otherwise, identifies a cut point that keeps the last N messages **without
     splitting tool pairs**: walk backwards from the end, grouping assistant
     messages with their subsequent tool-result messages. The cut falls between
     groups.
   - Replaces removed messages with a single summary message:
     `"[Previous conversation context compacted: N messages removed. The agent
     previously worked on this issue and made file changes. Continue from the
     current state.]"`
   - Saves and returns the compacted session.

3. Call `compactContextWindow()` from `AgentSessionService.toAiMessages()` — or
   better, from the callers that load a session before starting a loop run
   (e.g., `IssueImplementationService`, `FollowUpCommentService`).

4. Add Flyway migration `V27__agent_session_compaction_config.sql` if a new DB
   column is needed (likely not — config lives in `application.yml`).

**Assumptions:**
- `AgentSession.messages` is a `Set<ConversationMessage>` with `orphanRemoval=true`,
  so removing from the set cascades the delete.
- The summary message does not need to be high-fidelity — the agent can re-discover
  state via its tools (read files, check git log, etc.).

---

### Step 2: Add Sliding Window to `AgentLoop` (In-Memory Compaction)

**Goal:** Prevent the in-memory `history` list from growing unbounded during a single
`AgentLoop.run()` execution. This is the most impactful change — it prevents the
"prompt too long" crash that happens mid-run on complex issues with many tool calls.

**Components to create / modify:**
- [ ] Create: `agent/loop/HistoryCompactor` — tool-pair-aware sliding window logic
- [ ] Modify: `AgentLoop.run()` — call compactor after each round
- [ ] Modify: `AgentBudget` — add `maxHistoryChars` field

**Sequence:**

1. Add `maxHistoryChars` to `AgentBudget`:
   ```java
   public record AgentBudget(
       int maxRounds,
       int maxContextRounds,
       int maxValidationRetries,
       int maxTokensPerCall,
       int maxHistoryChars       // NEW: character budget for in-memory history
   ) {
       /** Backward-compatible constructor for callers that don't set maxHistoryChars. */
       public AgentBudget(int maxRounds, int maxContextRounds,
                          int maxValidationRetries, int maxTokensPerCall) {
           this(maxRounds, maxContextRounds, maxValidationRetries,
                maxTokensPerCall, 120_000);
       }
   }
   ```

2. Add `maxHistoryChars` to `AgentConfigProperties.BudgetConfig`:
   ```java
   /**
    * Character budget for the in-memory history list during a single
    * AgentLoop run. When exceeded, the HistoryCompactor prunes older
    * tool-pair groups and replaces them with a summary. Default: 120_000.
    */
   private int maxHistoryChars = 120_000;
   ```

3. Create `HistoryCompactor` in `agent/loop/`:
   ```java
   /**
    * Tool-pair-aware sliding window for AgentLoop's in-memory history.
    *
    * A "compaction unit" is either:
    * - A single user or assistant message (no tool calls), or
    * - An assistant message with tool_calls + all subsequent tool-role
    *   messages that reference those calls.
    *
    * Units are kept or dropped atomically. When the total character
    * count exceeds the budget, older units are replaced with a single
    * summary message.
    */
   public final class HistoryCompactor {

       private final int maxChars;
       private final int keepLastN;

       public HistoryCompactor(int maxChars, int keepLastN) { ... }

       /**
        * Compacts history in-place. Returns the number of characters freed.
        */
       public int compact(List<AiMessage> history) { ... }
   }
   ```

   The `compact()` algorithm:
   - Scan history into compaction units (group assistant+tool pairs).
   - Calculate total chars. If ≤ `maxChars`, return 0.
   - Drop oldest units until total is ≤ `maxChars` OR only `keepLastN`
     units remain.
   - Insert a summary `AiMessage(role="user", content="[Earlier conversation
     compacted to fit context window. Previous tool results are no longer
     available in history but their effects persist.]")` at position 0.

4. Wire it into `AgentLoop.run()`:
   - After the round's history appends (lines 118-146 in current `AgentLoop.java`),
     call `compactor.compact(history)`.
   - Log when compaction occurs: `log.info("AgentLoop compacted history for issue #{}: freed {} chars", ...)`.

5. Apply the same pattern to `E2eAgentRunner.run()` — its `history` list has
   the identical growth pattern. Add a `maxHistoryChars` constructor parameter
   and call `HistoryCompactor` after each round.

**Assumptions:**
- The system prompt already tells the agent it can re-read files and re-run
  tools, so losing old tool results from history is acceptable.
- Character count is a sufficient proxy for token count (roughly 4 chars ≈ 1 token
  for English/code). Exact token counting would require a tokenizer dependency.

---

### Step 3: Truncate Tool Results Before Adding to History

**Goal:** Prevent individual tool outputs from consuming the entire context budget.
Build logs, `cat` output on large files, and `tree` listings can easily produce
50k-200k chars of output that the model will never fully read.

**Components to create / modify:**
- [ ] Create: `agent/loop/ToolResultTruncator` — truncation + truncation marker
- [ ] Modify: `AgentLoop.run()` — truncate before `history.add()`
- [ ] Modify: `E2eAgentRunner.run()` — same
- [ ] Modify: `AgentConfigProperties.BudgetConfig` — add `maxToolResultChars`

**Sequence:**

1. Add config to `AgentConfigProperties.BudgetConfig`:
   ```java
   /**
    * Maximum characters retained from a single tool result in the
    * in-memory history. Longer results are truncated with a head+tail
    * strategy (first N/2 chars + last N/2 chars + truncation marker).
    * Default: 8_000.
    */
   private int maxToolResultChars = 8_000;
   ```

2. Create `ToolResultTruncator`:
   ```java
   /**
    * Truncates oversized tool results using a head+tail strategy that
    * preserves both the beginning (typically headers, structure) and the
    * end (typically errors, exit codes, summary lines) of the output.
    */
   public final class ToolResultTruncator {

       private final int maxChars;

       public ToolResultTruncator(int maxChars) {
           this.maxChars = maxChars;
       }

       /**
        * Returns the original text if ≤ maxChars, otherwise a truncated
        * version with a marker showing how many characters were removed.
        */
       public String truncate(String text) {
           if (text == null || text.length() <= maxChars) return text;
           int headSize = maxChars / 2;
           int tailSize = maxChars - headSize - 64; // 64 chars for marker
           int removed = text.length() - headSize - tailSize;
           return text.substring(0, headSize)
               + "\n\n[... " + removed + " characters truncated ...]\n\n"
               + text.substring(text.length() - tailSize);
       }
   }
   ```

3. Wire into `AgentLoop.run()` at line 134 (inside the `ContinueWithToolResults`
   block):
   ```java
   String truncated = truncator.truncate(r.resultText());
   history.add(AiMessage.builder()
       .role("tool")
       .toolCallId(r.toolCallId())
       .toolResult(truncated)
       .build());
   ```

4. Wire into `E2eAgentRunner.run()` at lines 221 and 252 (both legacy and native
   tool-result paths).

5. **Note:** The persisted session log (`sessionService.addMessage`) should also
   use truncated text to avoid DB bloat, but this is lower priority since the
   `compactContextWindow()` from Step 1 handles persisted history.

**Assumptions:**
- Head+tail truncation preserves more useful information than head-only truncation
  for build outputs (errors are typically at the end) and file contents (structure
  is at the beginning, end is often closing braces/boilerplate).
- 8,000 chars ≈ 2,000 tokens is enough for the model to understand a tool result
  without overwhelming the context.

---

### Step 4: Handle "Prompt Too Long" Errors with Retry-and-Compact

**Goal:** When a provider rejects a request because the prompt exceeds the model's
context window, the loop should recover automatically instead of crashing. This is the
last line of defense when Steps 2-3 weren't aggressive enough.

**Components to create / modify:**
- [ ] Modify: `AgentLoop.run()` — add try/catch with compaction-and-retry
- [ ] Modify: `E2eAgentRunner.run()` — same
- [ ] Modify: `AgentBudget` — add `maxPromptTooLongRetries`

**Sequence:**

1. Add config to `AgentConfigProperties.BudgetConfig`:
   ```java
   /**
    * Number of times the AgentLoop retries after a "prompt too long" error.
    * Each retry performs an aggressive compaction (drop 50% of remaining
    * history) before re-issuing the AI call. Default: 2.
    */
   private int maxPromptTooLongRetries = 2;
   ```

2. Modify `AgentLoop.run()` to wrap the AI call in a retry loop:
   ```java
   ChatTurn turn;
   int retries = 0;
   while (true) {
       try {
           // ... existing AI call code (lines 78-90) ...
           break;
       } catch (RuntimeException e) {
           if (retries < budget.maxPromptTooLongRetries()
                   && isPromptTooLongError(e)) {
               retries++;
               int freed = compactor.compactAggressively(history);
               log.warn("AgentLoop: prompt too long for issue #{}, "
                   + "aggressively compacted {} chars (retry {}/{})",
                   ctx.issueNumber(), freed, retries,
                   budget.maxPromptTooLongRetries());
               continue;
           }
           throw e;
       }
   }
   ```

3. Add `compactAggressively(List<AiMessage>)` to `HistoryCompactor`:
   - Drops all but the last 2 compaction units.
   - Inserts an urgent summary: `"[Context was aggressively compacted due to
     provider prompt-length limits. Only the most recent exchanges remain.]"`

4. Add `isPromptTooLongError(RuntimeException)` helper to `AgentLoop`.
   Delegate to `AbstractAiClient.isPromptTooLongError()` — but since `AgentLoop`
   only has the `AiClient` interface, either:
   - Expose `isPromptTooLongError` on the `AiClient` interface (preferred), or
   - Use string matching on the exception message as a fallback:
     ```java
     private static boolean isPromptTooLongError(RuntimeException e) {
         String msg = e.getMessage();
         if (msg == null) return false;
         String lower = msg.toLowerCase(Locale.ROOT);
         return lower.contains("prompt is too long")
             || lower.contains("context_length_exceeded")
             || lower.contains("maximum context length")
             || lower.contains("request too large");
     }
     ```

5. Apply the same retry pattern to `E2eAgentRunner.run()` (the catch block at
   line 191 currently returns a `budgetExhausted` result — add a retry path before
   giving up).

**Assumptions:**
- After aggressive compaction, the prompt will fit on retry. If it still doesn't
  (e.g., the system prompt alone is too large), the second retry fails and the
  error propagates normally.
- `isPromptTooLongError()` catches the common provider error patterns (OpenAI,
  Anthropic, local vLLM/Ollama). New providers may need additional patterns.

---

### Step 5: Add Token Usage Tracking to `AgentBudget`

**Goal:** Track cumulative token usage per agent run so the loop can proactively
compact before hitting hard limits, and so the dashboard/audit can show context
consumption per session.

**Components to create / modify:**
- [ ] Modify: `AgentBudget` — add mutable token usage counters
- [ ] Modify: `AgentLoop.run()` — record token usage from each AI response
- [ ] Modify: `AiClient` interface — expose `TokenUsage` in `ChatTurn`
- [ ] Modify: `AgentSessionService` — persist final token counts on the session

**Sequence:**

1. Since `AgentBudget` is a `record` (immutable), add a companion mutable
   tracker class:
   ```java
   /**
    * Mutable token usage tracker for a single AgentLoop run.
    * Thread-safe via AtomicLong (the loop itself is single-threaded,
    * but this allows future async tool execution).
    */
   public final class TokenUsageTracker {
       private final AtomicLong inputTokens = new AtomicLong();
       private final AtomicLong outputTokens = new AtomicLong();
       private final int contextWindowTokens;

       public TokenUsageTracker(int contextWindowTokens) {
           this.contextWindowTokens = contextWindowTokens;
       }

       public void record(long input, long output) {
           inputTokens.addAndGet(input);
           outputTokens.addAndGet(output);
       }

       public long totalTokens() {
           return inputTokens.get() + outputTokens.get();
       }

       /**
        * Returns true when cumulative input tokens exceed this percentage
        * of the model's context window, signalling that compaction should
        * be considered.
        */
       public boolean exceedsPercent(double percent) {
           return inputTokens.get() > (long)(contextWindowTokens * percent);
       }

       public long getInputTokens() { return inputTokens.get(); }
       public long getOutputTokens() { return outputTokens.get(); }
       public int getContextWindowTokens() { return contextWindowTokens; }
   }
   ```

2. Add `contextWindowTokens` to `AgentConfigProperties.BudgetConfig`:
   ```java
   /**
    * The model's context window size in tokens. Used by TokenUsageTracker
    * to determine when proactive compaction should kick in.
    * Default: 200_000 (Claude/GPT-4 class).
    * For local models: set to 32_768 or 16_384.
    */
   private int contextWindowTokens = 200_000;
   ```

3. Extend `ChatTurn` to carry token usage:
   ```java
   public record ChatTurn(
       String assistantText,
       List<ToolCall> toolCalls,
       String stopReason,
       TokenUsage tokenUsage    // NEW: may be null if provider doesn't report
   ) {
       public record TokenUsage(long inputTokens, long outputTokens) {}
       // ... existing factory methods ...
   }
   ```

4. Wire `TokenUsageTracker` into `AgentLoop.run()`:
   - Create tracker at start of `run()`.
   - After each AI call, record: `tracker.record(turn.tokenUsage())`.
   - When `tracker.exceedsPercent(0.7)`, trigger proactive compaction:
     ```java
     if (tracker.exceedsPercent(0.7)) {
         int freed = compactor.compact(history);
         log.info("Proactive compaction at 70% context usage: freed {} chars", freed);
     }
     ```

5. Persist final counts on the session:
   - Add `totalInputTokens` and `totalOutputTokens` columns to `agent_sessions`
     (Flyway `V27__agent_session_token_tracking.sql`).
   - At end of `AgentLoop.run()`, call:
     ```java
     sessionService.recordTokenUsage(ctx.session(),
         tracker.getInputTokens(), tracker.getOutputTokens());
     ```

6. Update AI client implementations to populate `ChatTurn.tokenUsage`:
   - `AnthropicAiClient`: parse `usage.input_tokens` / `usage.output_tokens`
     from the response JSON.
   - `OpenAiCompatibleClient`: parse `usage.prompt_tokens` / `usage.completion_tokens`.
   - `OllamaAiClient`: parse `prompt_eval_count` / `eval_count` from Ollama response.

**Assumptions:**
- Most providers return token usage in the response. For those that don't,
  `tokenUsage` is null and the tracker skips the recording.
- Character-based compaction (Steps 2-3) is sufficient as the primary defense.
  Token tracking is an enhancement for proactive compaction and auditing, not
  a replacement.
- Adding columns to `agent_sessions` is a non-breaking migration (nullable with
  default 0).

---

## Implementation Order & Dependencies

```
Step 1 (Session Compaction)     ─┐
                                  ├──→ Step 4 (Prompt-Too-Long Retry)
Step 2 (In-Memory Sliding Window)─┤
                                  │
Step 3 (Tool Result Truncation) ──┘
                                  │
Step 5 (Token Tracking) ──────────┘  (independent, can be done in parallel)
```

- **Steps 1, 2, 3** are independent and can be implemented in parallel.
- **Step 4** depends on Steps 2+3 (uses `HistoryCompactor` for the retry path).
- **Step 5** is independent but benefits from Steps 2+3 being in place first
  (the 70% threshold proactive compaction calls `HistoryCompactor`).

**Recommended order:** Step 3 → Step 2 → Step 1 → Step 4 → Step 5

Rationale: Tool truncation (Step 3) is the simplest and gives the biggest
immediate impact. In-memory sliding window (Step 2) is next. Session compaction
(Step 1) handles the resume case. Retry (Step 4) ties it all together. Token
tracking (Step 5) is the polish layer.

---

## Configuration Summary (application.yml)

```yaml
agent:
  context:
    max-tree-files: 500
    max-issue-comments: 50
    max-issue-comments-chars: 20000
    max-single-issue-comment-chars: 4000
    # NEW — Step 1
    agent-compaction-threshold-chars: 80000
    agent-compaction-keep-messages: 8
  budget:
    max-rounds: 10
    max-context-rounds: 3
    max-validation-retries: 3
    max-context-tool-requests-per-round: 5
    max-tokens-per-call: 16384
    # NEW — Step 2
    max-history-chars: 120000
    # NEW — Step 3
    max-tool-result-chars: 8000
    # NEW — Step 4
    max-prompt-too-long-retries: 2
    # NEW — Step 5
    context-window-tokens: 200000
```

---

## Testing Strategy

| Step | Unit Test | Integration Test |
|------|-----------|-----------------|
| 1 | `AgentSessionServiceTest.compactContextWindow_dropsOldMessages()` | Resume a session with 100k+ chars of history, verify compaction |
| 2 | `HistoryCompactorTest.compact_preservesToolPairs()` | Run `AgentLoop` with a mock that returns large tool results, verify history stays bounded |
| 3 | `ToolResultTruncatorTest.truncate_headTailStrategy()` | Verify truncated tool results in a full loop run |
| 4 | `AgentLoopTest.run_retriesOnPromptTooLong()` | Mock AI client to throw on first call, succeed after compaction |
| 5 | `TokenUsageTrackerTest.exceedsPercent()` | Verify token counts are persisted on session after loop completion |

---

## Future Enhancements (Out of Scope)

- **LLM-based summarization** of compacted history (higher quality than static text).
- **Per-model context window auto-detection** from provider metadata.
- **Dashboard widget** showing context usage percentage per active session.
- **Adaptive truncation** — dynamically adjust `maxToolResultChars` based on
  remaining context budget (more generous early, tighter later).
