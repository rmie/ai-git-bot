# Implementation Plan — Agentic PR Review Workflow

## Goal

Add a new `PrWorkflow` that reviews pull requests like the existing
`org.remus.giteabot.prworkflow.review.ReviewWorkflow`, but **agentic**: the LLM
can iteratively call built-in repository tools and MCP tools to explore the code
before writing its review. The bot may **only read** the repository — no
file-mutation, validation/build, or git-write tools are exposed, and no
commit / push / branch creation happens.

## Key architecture findings (research summary)

* `PrWorkflow` beans are auto-discovered by `PrWorkflowRegistry` and rendered in
  the workflow-selection form (`workflow-configurations/workflows.html`) purely
  from the registry + `paramsSchema()`. **No HTML change is required to add the
  new item to the form** — registering the bean is enough.
* The orchestrator (`PrWorkflowOrchestrator.runAll`) only runs workflows that an
  operator has explicitly selected for a bot's `WorkflowConfiguration` (bots
  without a configuration fall back to the legacy single `review` workflow). So
  the new workflow is opt-in by default.
* Agentic tool calling is provided by `AgentLoop` + an `AgentStrategy`, driving
  `AiClient.chatWithTools(...)` (native function calling) with descriptors from
  `ToolCatalog.nativeDescriptors(role, mcpCatalog, allowedBuiltinTools)`. Tool
  execution is routed through `AgentToolRouter`.
* `AgentToolRouter.Mode.WRITER` and `ToolCatalog.Role.WRITER` are **inherently
  read-only**: they expose only context tools (`cat`, `rg`, `find`, `tree`,
  `git-log`, `git-blame`, `branch-switcher`), the read-only issue helpers
  (`get-issue`, `search-issues`) and MCP tools. File / validation tools are
  *never* reachable in WRITER mode. → Reuse WRITER role/mode to guarantee
  "read-only" without touching the tool taxonomy.
* `AgentLoop` requires an `AgentSession` (persisted) for history bookkeeping; we
  reuse-or-create a session keyed on `(owner, repo, prNumber)`.
* System prompts live on the `SystemPrompt` entity (one TEXT column per agent
  role) seeded via Flyway (`db/migration/h2`). Adding a new role-prompt means:
  entity field + migration + `SystemPromptService` validation + controller
  preview/clone + `system-settings/form.html` textarea (+ `bots/form.html`
  preview, optional).
* Per-bot collaborators are assembled in `BotWebhookService`
  (`createIssueImplementationService` / `createWriterAgentService`); the new
  workflow needs the same beans, wrapped in a dedicated factory.

## Deliverables

### New package `org.remus.giteabot.prworkflow.agentreview`

1. **`AgentReviewWorkflow`** (`@Component`, implements `PrWorkflow`)
   * `key()` = `"agentic-review"`, `category()` = `REVIEW`,
     `displayName()` = `"Agentic PR Review"`.
   * `paramsSchema()`: `postReviewComment` (BOOLEAN, default true) and
     `maxToolRounds` (INTEGER, default from config) — small, optional tunables.
   * `run(context)`: resolve params, build the per-bot `AgentReviewService` via
     the factory, delegate.
2. **`AgentReviewServiceFactory`** (`@Component`) — mirrors
   `BotWebhookService.createIssueImplementationService`: resolves `AiClient`,
   `RepositoryApiClient`, MCP catalog/config, allowed built-in tools and builds
   an `AgentReviewService`.
3. **`AgentReviewContext`** (record) — per-bot collaborators (read-only review
   analogue of `IssueImplementationContext`).
4. **`AgentReviewService`** — core logic:
   * Fetch PR diff + title/body, build the kickoff user message.
   * Clone a **read-only** workspace at the PR head branch (`WorkspaceService`).
   * Reuse-or-create an `AgentSession`, run `AgentLoop` with
     `ReviewAgentStrategy`.
   * Post the final review as a single PR comment (when `postReviewComment`).
   * Always clean up the workspace.
5. **`ReviewAgentStrategy`** (implements `AgentStrategy`) — read-only:
   * `preferredToolMode()` = `NATIVE`,
     `toolDescriptors()` = `catalog.nativeDescriptors(Role.WRITER, …)`.
   * Native step: execute tool calls via `AgentToolRouter.Mode.WRITER`, feed
     results back. When the model returns text without tool calls → that text is
     the final review → `Finish(success, payload=review)`.
   * Legacy step (clients without native tools): treat the assistant text as the
     final review (graceful degradation).
   * `onBudgetExhausted`: finish with whatever text we last captured.

### System prompt ("Add a new system-prompt")

6. `SystemPrompt.reviewAgentSystemPrompt` TEXT column.
7. Flyway `V22__system_prompts_agentic_review.sql` (add column + seed default +
   NOT NULL).
8. `SystemPromptService.save` validation for the new field.
9. `SystemSettingsController`: clone setter + preview map entry.
10. `system-settings/form.html`: accordion textarea.
11. `bots/form.html`: preview rendering (optional, for parity).

### Documentation ("Add documentation like at the other pr-reviews")

12. `doc/PR_WORKFLOWS_AGENTIC_REVIEW.md` — feature doc.
13. Link it from `doc/PR_WORKFLOWS.md`.

## Read-only guarantee (defense in depth)

* `Role.WRITER` descriptors expose no write tools → the model is never told it
  can write.
* `Mode.WRITER` routing rejects any non-context / non-issue / non-MCP tool.
* The service never calls `commit`, `push`, `createPullRequest`, branch
  creation, or any `postReviewAction` (approve / request-changes); it only posts
  a normal PR comment.

## Out of scope

* New `ToolCatalog.Role.REVIEW` / `AgentToolRouter.Mode.REVIEW` (WRITER already
  gives the exact read-only surface we need).
* Multi-dialect migrations (repo ships only the `h2` migration folder).

