# Implementation Plan — `unit-test-author` PR Workflow

> Status: **IMPLEMENTED** · Category: `TESTING` · Persona: developer / tech-lead ·
> Deployment target: **none** · Opt-in per bot via workflow-selection UI.
>
> Operator-facing docs: [`../PR_WORKFLOWS_UNIT_TEST.md`](../PR_WORKFLOWS_UNIT_TEST.md).
>
> **Divergences from this plan as built:**
> * The per-framework runner classes + `UnitTestRunnerRegistry` (SPI) were
>   collapsed into a **single framework-agnostic `UnitTestRunner`** driven by
>   `UnitTestRunRequest.framework()` — adding a toolchain is now just a new
>   `UnitTestFramework` enum value.
> * The runner executes the project's build/test tool through the coding agent's
>   existing **`ToolExecutionService`** (allow-listed + timed out via
>   `agent.validation.*`) instead of a bespoke `WorkspaceProcessRunner`.
> * Toolchains shipped: `maven`, `gradle`, `npm`, `pytest`, `go`, `cargo`,
>   `dotnet`, `bundle`, `make`, `gcc`, `g++` (broader than the original
>   maven/npm/pytest/go set).
> * Slash commands are `@bot generate-tests` / `@bot rerun-unit-tests`.
> * MVP lifecycle is `commit-to-pr` (default) or `ephemeral` (report only); the
>   `promotionThresholdPercent` param was dropped as unused.

## 1. Goal & scope

Add a new **read-write** PR workflow `unit-test-author` that, for every opened /
synchronised PR (and on the `@bot write-unit-tests` slash command), generates or
extends **unit tests** for the files touched by the diff, runs the project's own
test runner (`mvn test` / `npm test` / `pytest` / `go test`), commits the new
tests onto the PR's branch and posts a result + coverage-delta comment.

It is **explicitly different** from the shipped `e2e-test` workflow:

| Aspect                | `e2e-test` (existing)                         | `unit-test-author` (new)                                   |
|-----------------------|-----------------------------------------------|------------------------------------------------------------|
| Workspace             | empty sandbox, **no repo source**             | **full checkout of the PR head branch** (real source)      |
| Needs preview env     | yes (DeploymentTarget)                         | **no**                                                     |
| Browser               | yes (Playwright/Cypress)                       | **no**                                                     |
| Test kind             | black-box journeys via preview URL            | white-box unit tests next to existing tests                |
| Runner                | `npx playwright test`, …                       | project runner: `mvn test` / `npm test` / `pytest` / `go test` |
| Output side-effect    | optional follow-up PR / commit                 | **commit test files onto the PR branch** + PR comment      |

Because the agent must **read the repository source and write files into it**,
the implementation follows the **coding-agent infrastructure**
(`IssueImplementationService` + `CodingAgentStrategy`, `WorkspaceService`
checkout + `commitAndPush`) for the file-writing parts, and reuses the
**`prworkflow/e2e`** structure (suite/case persistence, runner SPI, params,
summary renderer, slash-command handler, lifecycle/promotion) for the
test-orchestration parts.

## 2. Reference patterns studied

* `org.remus.giteabot.prworkflow.e2e.*` — `PrWorkflow` bean, `E2eTestParam`,
  `SuiteLifecycleMode`, `PrTestSuite`/`PrTestCase`(+repos), `TestSuiteRunner`
  SPI + `TestSuiteRunnerRegistry`, `PrWorkflowToolExecutor`/`Context`,
  `WorkspaceProcessRunner`, `E2eTestSummaryRenderer`,
  `E2eTestSlashCommandHandler`, `SuitePromotionService`, `E2ePromptLibrary`.
* `org.remus.giteabot.prworkflow.agentreview.*` — per-bot service `Factory` +
  `Context`, `AgentLoop` + custom `AgentStrategy`, `AgentToolRouter`,
  read-only system-prompt assembly.
* `org.remus.giteabot.agent.IssueImplementationService` + `issueimpl.CodingAgentStrategy`
  — full repo checkout (`WorkspaceService.prepareWorkspace`), `Role.CODING`
  write tools (`patch-file`, `mkdir`, `delete-file`), `commitAndPush(...)`.
* System prompt plumbing — `SystemPrompt` entity, `SystemPromptService`
  validation, `SystemSettingsController` clone/preview, `system-settings/form.html`
  accordion, Flyway `h2` + `postgresql` migrations (latest = **V22**).

## 3. New Java package: `org.remus.giteabot.prworkflow.unittest`

```
prworkflow/unittest/
  UnitTestWorkflow.java               // PrWorkflow bean, key "unit-test-author", category TESTING
  UnitTestParam.java                  // enum WorkflowParamName (4 params, mirrors E2eTestParam)
  UnitTestFramework.java              // enum: MAVEN, NPM, PYTEST, GO (key + runner command)
  UnitTestSlashCommandHandler.java    // "@bot write-unit-tests" → orchestrator.run(...)
  UnitTestSummaryRenderer.java        // Markdown comment incl. coverage delta
  UnitTestPrCloseHandler.java         // (optional) PROMOTE_ON_MERGE handling, mirrors E2eTestPrCloseHandler
  UnitTestSuiteServiceFactory.java    // per-bot construction (Ai/Repo client, MCP, tool whitelist)
  UnitTestSuiteService.java           // checkout + author loop + run + commit/push + comment
  runner/
    UnitTestRunner.java               // SPI: framework() + run(UnitTestRunRequest)
    UnitTestRunnerRegistry.java       // resolve runner by framework
    MavenUnitTestRunner.java          // `mvn -q test` (+ optional jacoco)
    NpmUnitTestRunner.java            // `npm test` / `npm run test`
    PytestUnitTestRunner.java         // `pytest -q` (+ coverage.py)
    GoUnitTestRunner.java             // `go test ./...` (+ -cover)
    UnitTestOutcome.java              // status + summary + attempted/failed + coverageDelta
    UnitTestOutcomeStatus.java        // PASSED / FAILED / ERROR / SKIPPED
    UnitTestRunRequest.java           // context, bot, payload, suite, workspace, framework, retries, caps
  agents/
    UnitTestPromptLibrary.java        // editable role + non-editable protocol suffix (per stage)
    UnitTestAuthorAgent.java          // writes test files via CODING tools into checkout
    (reuse e2e agents/NarratedToolCallParser via shared util if needed)
  coverage/
    CoverageParser.java              // parse jacoco.xml / coverage.xml / lcov / `go test -cover`
    CoverageDelta.java               // before/after %, per-file deltas
```

### 3.1 Entities — reuse vs. new

The `e2e` suite/case entities (`PrTestSuite`, `PrTestCase`) are coupled to
`E2eTestFramework` and the `runId`/`prNumber` schema. **Decision:** add a
parallel pair `UnitTestSuite` / `UnitTestCase` (+ repositories + Flyway tables)
to avoid overloading the e2e domain and its `framework` enum. They mirror the
e2e entities 1:1 (run id, pr number, source tree ref, lifecycle mode, cases with
path/title/content/lastStatus/lastLog/lastDurationMs). `SuiteLifecycleMode` is
**reused as-is** from the `e2e` package (it is framework-agnostic).

> Alternative considered: generalise `PrTestSuite` with a `kind` discriminator.
> Rejected for this iteration — larger blast radius on existing e2e code/tests.

## 4. Workflow parameters (`UnitTestParam`)

Exactly the four the issue lists, mirroring `E2eTestParam` semantics, persisted
in `workflow_selection_params` and editable on the workflow-edit page via
`paramsSchema()`:

| Param key                   | Label                              | Type / default | Notes |
|-----------------------------|------------------------------------|----------------|-------|
| `maxRetries`                | Max retries per test               | INT, `1` (0–5) | re-run failing test up to budget; flaky tagging |
| `maxTestCases`              | Max test cases per suite           | INT, `20` (cap 100) | cost guard, hard cap `ABSOLUTE_MAX_TEST_CASES` |
| `suiteLifecycle`            | Suite lifecycle                    | ENUM `SuiteLifecycleMode` | EPHEMERAL / OFFER_AS_PR / PROMOTE_ON_MERGE / COMMIT_TO_PR |
| `promotionThresholdPercent` | Promotion pass-rate threshold (%)  | INT, `100` (0–100) | min pass-rate for promotion modes |

Plus one **framework** selector (auto-detect default) so the runner is
deterministic, mirroring `E2eTestParam.FRAMEWORK`:

| `framework` | Test framework | ENUM `UnitTestFramework` | `auto` (detect) / maven / npm / pytest / go |

`COMMIT_TO_PR` is the natural default behaviour here (tests are committed onto
the PR branch); `OFFER_AS_PR` / `PROMOTE_ON_MERGE` reuse `SuitePromotionService`
semantics. For the MVP the workflow always commits to the PR branch and treats
the lifecycle param the same way `E2ETestWorkflow` does.

## 5. Orchestration flow (`UnitTestSuiteService`)

1. Resolve params + framework (auto-detect from repo files: `pom.xml`→MAVEN,
   `package.json`→NPM, `pyproject.toml`/`pytest.ini`→PYTEST, `go.mod`→GO).
2. Ack comment (mirrors `E2eTestSummaryRenderer.renderStarting`) + `appendStep`.
3. `WorkspaceService.prepareWorkspace(owner, repo, headBranch, cloneUrl, token)`
   — **full checkout of the PR head branch** (read-write, real source).
4. Fetch PR diff; build author kickoff message (changed files + tree context).
5. Persist a draft `UnitTestSuite` row.
6. **Author loop** — `AgentLoop` + `UnitTestAuthorAgent`/strategy using
   `ToolCatalog.Role.CODING` + `AgentToolRouter.Mode.CODING` so the agent can
   `cat`/`rg`/`tree` the source **and** `patch-file`/`mkdir` new test files into
   a **separate test package next to existing tests** (e.g.
   `src/test/java/...`, `__tests__/`, `tests/`, `*_test.go`). The prompt's
   non-editable protocol suffix pins: "create tests in the conventional test
   source set, never modify production code, one logical test class/file per
   changed unit, no TODOs/stubs". Persist `UnitTestCase` rows for each written
   file.
7. **Run** — `UnitTestRunnerRegistry.find(framework).run(request)` executes the
   project runner via `WorkspaceProcessRunner` inside the checkout; parse
   results + coverage; update per-case `UnitTestCaseStatus`. Retry failing
   tests up to `maxRetries`.
8. **Commit & push** the new test files onto the PR branch via
   `WorkspaceService.commitAndPush(workspaceDir, headBranch, msg, AUTHOR_NAME,
   AUTHOR_EMAIL, false /* existing branch */)`. (For OFFER_AS_PR /
   PROMOTE_ON_MERGE, hand off to `SuitePromotionService` instead — gated by
   `promotionThresholdPercent`, exactly like `E2ETestWorkflow`.)
9. Post a **PR comment** with the run summary and **inline coverage delta**
   (`UnitTestSummaryRenderer`), using `postPullRequestComment` (test-result
   comment) — not a formal review.
10. `finally` → `workspaceService.cleanupWorkspace(workspaceDir)`.

`UnitTestWorkflow.run(...)` resolves params, guards `context.requireActive(...)`,
delegates to `serviceFactory.create(bot).authorAndRun(payload, params)` and maps
the `UnitTestOutcome` onto a `WorkflowResult` (PASSED→success, SKIPPED→skipped,
FAILED/ERROR→failed), mirroring `E2ETestWorkflow.mapOutcome`.

## 6. Read/write tool surface (three layers)

* **Tool surface:** advertise `ToolCatalog.Role.CODING` descriptors (context +
  file-write tools + MCP) — the agent must write test files.
* **Execution routing:** `AgentToolRouter.Mode.CODING`.
* **Workflow side-effects:** writes are confined to test source sets by the
  prompt protocol + a path guard in the author agent (reject writes outside the
  framework's conventional test directory, analogous to
  `PrWorkflowToolExecutor.prTestWrite`'s `allowedPrefix`), and only the PR's own
  branch is pushed. Production source is never modified (guard + commit-diff
  assertion before push; abort if non-test files changed).

## 7. Test runners & coverage

`WorkspaceProcessRunner` (reused from `prworkflow.e2e.tools`) runs the command
inside the checkout with a timeout + output cap. Per framework:

| Framework | Run command (default)            | Coverage source parsed by `CoverageParser` |
|-----------|----------------------------------|--------------------------------------------|
| MAVEN     | `mvn -q -B test`                 | `target/site/jacoco/jacoco.xml` (if jacoco) |
| NPM       | `npm test` (or `npm run test`)   | `coverage/lcov.info` / `coverage-final.json` |
| PYTEST    | `pytest -q` (`--cov` if present) | `coverage.xml` |
| GO        | `go test ./... -cover`           | stdout `coverage: NN.N% of statements` |

Coverage delta is best-effort: capture baseline (run on the unmodified checkout
*before* writing new tests, or read an existing report), then re-run after; if
baseline can't be computed, the comment shows post-run coverage only. Failure to
parse coverage never fails the workflow.

## 8. System prompt (operator-editable)

Add **one** new `SystemPrompt` column `unitTestAuthorSystemPrompt` (the workflow
uses a single author agent; runner behaviour is deterministic Java, not an LLM
stage — so unlike e2e we do not need planner/author/runner triplets).

Touch points (mirror `reviewAgentSystemPrompt` / `e2e*` precedent):

1. `SystemPrompt.java` — new `@Column(nullable=false, columnDefinition="TEXT")
   private String unitTestAuthorSystemPrompt;`
2. `UnitTestPromptLibrary` — `DEFAULT_AUTHOR_EDITABLE` (role) +
   `AUTHOR_PROTOCOL_SUFFIX` (non-editable: tool names, test-dir rules, "no prod
   changes", framework placeholder) + `authorSystemPromptOrDefault(...)`.
3. Flyway **V23** (both dialects):
   * `src/main/resources/db/migration/h2/V23__system_prompts_unit_test.sql`
   * `src/main/resources/db/migration/postgresql/V23__system_prompts_unit_test.sql`
     (Postgres uses `$tag$…$tag$` dollar-quoting for the seeded default, per V20/V22.)
   Each: `ALTER TABLE system_prompts ADD COLUMN IF NOT EXISTS
   unit_test_author_system_prompt TEXT;` → `UPDATE … WHERE … IS NULL;` →
   `ALTER … SET NOT NULL;`
4. `SystemPromptService.save(...)` — add required-field validation.
5. `SystemSettingsController` — clone setter + add to `/preview` response map.
6. `templates/system-settings/form.html` — new accordion item with an info
   alert (role-only editable) + textarea bound to `*{unitTestAuthorSystemPrompt}`.

## 9. Triggers / wiring

* **PR opened/synchronized:** automatic, because the workflow is a `PrWorkflow`
  bean enumerated by `PrWorkflowRegistry` and run by `PrWorkflowOrchestrator`
  when enabled on the bot's `WorkflowConfiguration` (opt-in, like e2e/agentic-review).
* **Slash command `@bot write-unit-tests`:** `UnitTestSlashCommandHandler`
  mirrors `E2eTestSlashCommandHandler` (👀 reaction, PR hydration for GitHub
  `issue_comment`, enabled-check via `WorkflowSelectionService.enabledWorkflowKeys`,
  `orchestrator.run(bot, payload, UnitTestWorkflow.KEY, hints)`). Register the
  command in whatever dispatches comment slash-commands (alongside the e2e
  handler — check `CodeReviewService`/webhook comment routing) so it is tried
  before the default comment-as-prompt fallback.

## 10. Persistence (Flyway)

New tables `unit_test_suites` / `unit_test_cases` mirroring the e2e DDL
(`V*_e2e_*` migrations) — both dialects, **V24** (after the V23 prompt column):
* `src/main/resources/db/migration/h2/V24__unit_test_suites.sql`
* `src/main/resources/db/migration/postgresql/V24__unit_test_suites.sql`

Columns mirror `PrTestSuite`/`PrTestCase` (id, run_id, pr_number, framework,
source_tree_ref, lifecycle_mode, created_at; case: suite_id FK, path, title,
content, last_status, last_log, last_run_at, last_duration_ms).

## 11. Tests (mirror existing coverage)

* `UnitTestWorkflowTest` — params schema, key/category, outcome→result mapping,
  skip when no diff / unsupported framework.
* `UnitTestSuiteServiceTest` — framework auto-detection, author→run→commit happy
  path with stub `AiClient`/`RepositoryApiClient`/`WorkspaceService`, prod-file
  write guard rejection.
* `Maven/Npm/Pytest/GoUnitTestRunnerTest` — command construction + result/coverage
  parsing from fixture reports.
* `CoverageParserTest` — jacoco.xml / coverage.xml / lcov / go-stdout fixtures.
* `UnitTestSlashCommandHandlerTest` — pattern match, enabled-check gating,
  dispatch + 👀 reaction (mirror `E2eTestSlashCommandHandler` tests).
* `UnitTestPromptLibraryTest` — editable+suffix assembly, framework placeholder.
* Migration smoke (existing Flyway test harness picks up V23/V24 automatically).

## 12. Documentation

* `doc/PR_WORKFLOWS_UNIT_TEST.md` — architecture, flow diagram, params,
  per-framework runner/coverage table, enabling instructions, provider support,
  read/write safety model. Follows `doc/PR_WORKFLOWS_AGENTIC_REVIEW.md` format.
* Link it from `doc/PR_WORKFLOWS.md` (index) and mention in `README.md` workflow
  list + `CHANGELOG.md`.
* Optional: `doc/agentic-workflows/UNIT_TEST_USER_STORY.md` following the
  existing `*_USER_STORY.md` style.

## 13. Build / migration ordering summary

| Order | Artifact |
|-------|----------|
| V23   | `system_prompts.unit_test_author_system_prompt` (h2 + postgres) |
| V24   | `unit_test_suites` / `unit_test_cases` tables (h2 + postgres) |

## 14. Out of scope (this iteration)

* Mutation testing / coverage *gates* that fail the PR.
* Languages beyond maven/npm/pytest/go (extensible via `UnitTestRunner` SPI).
* Generalising the e2e suite entities under a shared discriminator.

## 15. Activation

Opt-in per bot via the workflow-selection UI. Does **not** replace or duplicate
the `e2e-test` or standard review workflows; it coexists in category `TESTING`.


