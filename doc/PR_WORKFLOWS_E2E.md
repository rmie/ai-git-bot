# PR Workflows: E2E Tests
The `E2ETestWorkflow` is the non-review workflow shipped on top of the
agentic PR-workflow platform. It runs after a pull request is opened or
synchronised and follows the four-stage pipeline described in
[`CONCEPT_AND_ARCHITECTURE.md`](./agentic-workflows/CONCEPT_AND_ARCHITECTURE.md):
```
plan → deploy → author → run → comment
```
The full agentic pipeline (planner → author → runner) is wired through
`PlaywrightTestSuiteRunner`. Operators drive it via the seeded
`Full-stack QA` workflow configuration, the two slash commands
`@bot rerun-tests` and `@bot regenerate-tests [feedback]`, and PR-close
teardown that honours `SuiteLifecycleMode`. All four repository providers
implement `attachPullRequestArtifact` (GitLab uploads, Gitea issue
assets, Bitbucket downloads; GitHub keeps the inline default — see
below); operator feedback from `regenerate-tests` is threaded into the
planner's user message via `PrWorkflowContext.hints`.
> 📸 *Screenshot placeholder — example E2E run comment on a PR*
> `doc/screenshots/prworkflow/pr-comment-e2e-report.png` (TODO)
## Enabling the workflow on a bot

1. Make sure the bot has a **deployment target** configured under
   *System settings → Deployment targets* (see [`PR_WORKFLOWS.md`](./PR_WORKFLOWS.md)
   for the available strategies — `STATIC`, `WEBHOOK`, `MCP`, and
   `CI_ACTION`, all shipped; cross-reference the persona-driven
   walk-throughs under [`doc/agentic-workflows/`](./agentic-workflows/README.md) if
   you're unsure which one to pick). Without a target the workflow
   aborts immediately and posts a clearly labelled skip-comment.

   > 💡 If you run the bot itself via Docker / `docker compose` and use a
   > `CI_ACTION` deployment target, make sure the bot's public callback base URL
   > is reachable **from inside the workflow job container**. In Docker-based
   > setups configure this via `APP_PUBLIC_URL` (maps to Spring property
   > `app.public-url`), for example:
   >
   > ```yaml
   > APP_PUBLIC_URL: http://172.17.0.1:8080
   > ```
   >
   > on Linux / WSL2, or:
   >
   > ```yaml
   > APP_PUBLIC_URL: http://host.docker.internal:8080
   > ```
   >
   > on macOS / Windows with Docker Desktop. If this is left at
   > `http://localhost:8080`, E2E preview callbacks can fail because `localhost`
   > inside the job container points to the container itself, not to the bot.
2. Open the bot's *Workflow configuration*. Either pick the **seeded
   `Full-stack QA` configuration** (shipped by Flyway `V18`, has
   `review` + `e2e-test` pre-enabled with `framework=playwright`,
   `maxRetries=1`, `maxTestCases=10`) or toggle the `E2E Tests` workflow
   on your own configuration (the workflow is listed under **TESTING**
   and is **disabled by default** on the seeded `Default` configuration).
   Tune the per-workflow parameters as needed:

   | Field            | Type    | Default      | Notes                                                                |
   |------------------|---------|--------------|----------------------------------------------------------------------|
   | `framework`      | string  | `playwright` | One of `playwright`, `pytest`, `k6`, `cypress`. Wave 1 ships only `playwright`. |
   | `maxRetries`     | integer | `1`          | Per-test retry budget. A test that passes after retry is tagged `FLAKY`. Capped at 5. |
   | `maxTestCases`   | integer | `20`         | Hard cost guard. Capped at 100 server-side regardless of the configured value. |

3. Save. The next PR-open / PR-synchronise webhook triggers the workflow.

## Customising the agent prompts

The three E2E agents (**planner**, **author**, **runner**) ship with
sensible built-in role descriptions, but you can override each one per
**System Prompt entry** under *System settings → System prompts*. The
form exposes three additional editors below the existing
review / issue-agent / writer-agent slots:

- **E2E Planner System-Prompt** — persona and policy for the agent that
  reads the PR diff and produces the test plan.
- **E2E Author System-Prompt** — persona and policy for the agent that
  materialises each planned journey as a runnable test file.
- **E2E Runner System-Prompt** — persona and policy for the agent that
  executes the suite against the preview deployment and reports the
  outcome.

The bot's *Preview* button renders all three texts alongside the
existing review / coding / writer prompts so you can sanity-check them
before saving.

> 🛡️ **What you can and cannot edit.** These three editors hold the
> agent's **role description only** — persona, intent, tone, policy.
> The *technical protocol* is appended automatically by the software at
> runtime and is **not** editable from the UI:
>
> - the active test framework key (`playwright`, `cypress`, …)
> - the planner's JSON output schema (`framework`, `journeys[]`,
>   `maxRetries`, …)
> - the author's required tool call (`pr-test-write`) and the strict
>   URL handling rules (`page.goto('/…')` / `cy.visit('/…')`,
>   never `process.env.BASE_URL`)
> - the runner's tool sequence (`preview-url` → `preview-status` →
>   `pr-test-run` → optional `attach-artifact`) and the retry / FLAKY
>   semantics
>
> That split means an operator can freely tune *how* the agents speak
> and *what they prioritise*, but cannot accidentally break the JSON
> contract, the tool dispatch, or the URL conventions the runner relies
> on. The fallback used when a slot is left blank is the corresponding
> `DEFAULT_*_EDITABLE` constant in
> `org.remus.giteabot.prworkflow.e2e.agents.E2ePromptLibrary`.

## What it does (per run)

1. Resolves the workflow parameters from the bot's configuration.
2. Aborts cleanly (PR comment + `WorkflowResult.SKIPPED`) if no deployment
   target is configured on the bot.
3. Persists a draft `pr_test_suites` row tied to the active
   `PrWorkflowRun`.
4. Hands off to `DeploymentOrchestrator.requestDeployment(...)`. Failure /
   timeout / rejection are surfaced as a "❌ Failed" PR comment.
5. Allocates a sandboxed workspace under
   `${ai-git-bot.e2e.workspace-root:${java.io.tmpdir}/ai-bot-pr-tests}/run-<id>/`
   with framework-specific scaffolding (`package.json` +
   `playwright.config.ts` for Playwright). Path traversal is denied by
   `PrTestWorkspaceManager.resolveInsideWorkspace(...)`.
6. Dispatches to the registered `TestSuiteRunner` for the chosen framework
   — `PlaywrightTestSuiteRunner` in wave 2, which drives the three agents
   (`TestPlannerAgent`, `TestAuthorAgent`, `TestRunnerAgent`) through the
   existing `AgentLoop` / `chatWithTools` infrastructure. The agents use
   only the `PR_WORKFLOW`-category tools registered in `ToolCatalog`
   (`pr-test-write`, `pr-test-run`, `preview-url`, `preview-status`,
   `attach-artifact`).
7. Posts the run summary as a PR comment, e.g.:

   ```markdown
   ## E2E Test Run for PR #42

   **Framework:** `playwright`
   **Preview environment:** https://preview-42.example.com
   **Source SHA:** `abc12345`
   **Outcome:** ❌ FAILED (1/2 passed)

   | Test | Status | Duration |
   | --- | --- | --- |
   | `tests/login.spec.ts`<br/>Sign-in happy path | ✅ PASSED | 1.23s |
   | `tests/checkout.spec.ts`<br/>Add to cart and pay | ❌ FAILED | 4.57s |

   > 1 of 2 failed
   ```

## Lifecycle on PR close

`E2eTestPrCloseHandler` runs from `BotWebhookService.handlePrClosed(...)`.
For every `e2e-test` run on the closed PR it:

1. Broadcasts `DeploymentStrategy.teardown(...)` across every registered
   strategy so the preview environment is released (the default
   `teardown()` is a no-op, so strategies that don't recognise the handle
   return silently).
2. Removes the sandboxed workspace on disk.
3. Deletes every `pr_test_suites` row for the PR whose `lifecycle_mode` is
   `ephemeral` **or** `commit-to-pr` (the latter has already pushed its
   content during the workflow run). Suites tagged `offer-as-pr` or
   `promote-on-merge` are kept so the dashboard can correlate the parent
   run with the follow-up PR; a future GC pass eventually removes them.
4. For `promote-on-merge` suites only — and only when the parent PR was
   actually **merged** — invokes
   `SuitePromotionService.promote(...)` to open the follow-up PR against
   the repository's default branch. See
   [Suite lifecycle modes (M7)](#suite-lifecycle-modes-m7) below.

Failures are logged but never abort the close handler.

## Suite lifecycle modes (M7)

`E2ETestWorkflow.paramsSchema()` exposes a `suiteLifecycle` param that
controls what happens to the generated `PrTestSuite` once the run is
done. The four modes are implemented by `SuitePromotionService`:

| Mode | When promotion fires | Target branch | Target directory | Opens follow-up PR? |
|---|---|---|---|---|
| `ephemeral` (default) | — | — | — | no |
| `offer-as-pr` | immediately after `outcome == PASSED` | parent feature branch | `tests/e2e/pr-{n}/` | yes — `ai-tests/pr-{n}` |
| `promote-on-merge` | on PR close, only if `merged == true` and backing run was SUCCESS | repository default branch | `tests/e2e/` | yes — `ai-tests/promoted-pr-{n}` |
| `commit-to-pr` | immediately after `outcome == PASSED` | parent feature branch | `tests/e2e/pr-{n}/` | no — commits directly |

### Idempotency

`PrWorkflowRun.followUpPrNumber` (column `follow_up_pr_number`, added
by migration `V19`) is set on the first successful promotion. Subsequent
triggers (re-runs, `@bot rerun-tests`, late merge events) see the column
populated and short-circuit with an `ALREADY_PROMOTED` outcome — no
duplicate branches, no duplicate PRs.

### Conflict policy

When the destination file already exists in the target branch (or
collides with an earlier case in the same run), `SuitePromotionService`
appends a numeric suffix before the first dot:
`login.spec.ts` → `login_2.spec.ts` → `login_3.spec.ts`. The chosen
final paths are listed in the follow-up PR description.

### Failure semantics

Workspace failures, `git push` failures, and `createPullRequest`
failures all surface as an `Outcome.FAILED` and a `❌ Promotion failed
— …` comment on the parent PR. The parent run's terminal status is
never rolled back, and the deployment / workspace teardown still runs.

### Security note

Promoted tests run in the standard CI pipeline of the repository, which
typically has access to a different / wider secret scope than the
sandbox the bot generated them in. **Review every promoted PR for
hidden credential references, hard-coded URLs pointing at the preview
environment, and assumptions about the preview-only feature flags.**
The follow-up PR body explicitly calls this out.

### Operator recipe

The runnable laptop walkthrough lives at
[`systemtest/README-suite-promotion.md`](../systemtest/README-suite-promotion.md).

## Persistence

See `V17__pr_test_suites.sql` (H2 + PostgreSQL):

```text
pr_test_suites(id, run_id FK, pr_number, framework, source_tree_ref,
               lifecycle_mode, created_at)
pr_test_cases (id, suite_id FK, path, title, content,
               last_status, last_run_at, last_duration_ms, last_log)
```

`run_id` cascades from `pr_workflow_runs(id)` so deleting a run cleans up
its suite and cases. The `cases.content` column holds the full generated
test source inline — the bot does not re-clone anything to re-run.

## Where to look in the code

| Concern                                                | Class                                                                 |
|--------------------------------------------------------|-----------------------------------------------------------------------|
| Workflow entry point                                   | `org.remus.giteabot.prworkflow.e2e.E2ETestWorkflow`                   |
| Persistence model                                      | `org.remus.giteabot.prworkflow.e2e.{PrTestSuite,PrTestCase}`          |
| Sandbox + path-traversal guards                        | `org.remus.giteabot.prworkflow.e2e.workspace.PrTestWorkspaceManager`  |
| Pluggable runner SPI                                   | `org.remus.giteabot.prworkflow.e2e.runner.TestSuiteRunner`            |
| Default no-op runner (kept for tests/docs only) | `org.remus.giteabot.prworkflow.e2e.runner.NoopTestSuiteRunner`        |
| Playwright runner driving the three agents (M4 wave 2) | `org.remus.giteabot.prworkflow.e2e.runner.PlaywrightTestSuiteRunner`  |
| Planner / Author / Runner agents (M4 wave 2)           | `org.remus.giteabot.prworkflow.e2e.agents.*`                          |
| Sandboxed tool executor (M4 wave 2)                    | `org.remus.giteabot.prworkflow.e2e.tools.PrWorkflowToolExecutor`      |
| Slash-command dispatcher (M4 wave 2)                   | `org.remus.giteabot.prworkflow.e2e.E2eTestSlashCommandHandler`        |
| PR-close teardown                                      | `org.remus.giteabot.prworkflow.e2e.E2eTestPrCloseHandler`             |
| Markdown rendering                                     | `org.remus.giteabot.prworkflow.e2e.E2eTestSummaryRenderer`            |

## Slash commands

The dispatcher `E2eTestSlashCommandHandler` intercepts comments on the
PR before the regular code-review handler runs:

| Command                                  | Effect                                                                       |
|------------------------------------------|------------------------------------------------------------------------------|
| `@bot rerun-tests`                       | Re-triggers the `e2e-test` workflow for the PR (creates a fresh `PrWorkflowRun`). |
| `@bot regenerate-tests [feedback...]`    | Re-triggers the workflow. The trailing free-text is captured for the planner. |

Both commands are no-ops if the bot's `WorkflowConfiguration` does not
have `e2e-test` enabled — the comment falls through to the standard
code-review handler so the bot keeps answering free-form mentions
exactly as before.

## Try it out — sample app under `systemtest/`

A minimal Node app (no external deps, ~70 lines of `server.js`) lives
under `systemtest/sample-e2e-app/` and is
exposed via [`systemtest/docker-compose-e2e-sample.yml`](../systemtest/docker-compose-e2e-sample.yml).
It boots a single login form with credentials `demo` / `demo` and exposes
`/healthz` for the deployment-target probe.

```bash
docker compose -f systemtest/docker-compose-e2e-sample.yml up --build
# In the bot UI:
#   1. System settings → Deployment targets → New
#        Strategy:               STATIC
#        Preview URL template:   http://sample-e2e-app:3000   (or http://host.docker.internal:3030)
#        Health-check path:      /healthz
#   2. Bot → Workflow configuration: pick "Full-stack QA"
#   3. Bot → Deployment target: pick the one created above
# Open a PR → the bot generates Playwright specs, runs them against the sample
# app and posts the run summary back on the PR.
```

## Suite lifecycle modes (M7)

By default a generated test suite is **ephemeral** — the bot deletes the
suite (and its `PrTestCase` rows) the moment the parent PR closes.
The `e2e-test` workflow exposes a single `suiteLifecycle` param that
graduates good suites from "throwaway scratchpad" to "real code in the
repo". Four modes are supported:

| `suiteLifecycle` | Trigger | Target branch | Target dir | Resulting follow-up PR |
|---|---|---|---|---|
| `ephemeral` *(default)* | — | — | — | None — suite is deleted on PR close. |
| `commit-to-pr` | Successful run on the feature PR | feature branch (direct commit) | `tests/e2e/pr-{n}/` | None — tests land directly on the feature branch. |
| `offer-as-pr` | Successful run on the feature PR | `ai-tests/pr-{n}` → feature branch | `tests/e2e/pr-{n}/` | "Add E2E tests for PR #N" — the author reviews tests in isolation. |
| `promote-on-merge` | PR-merged webhook on the parent PR | `ai-tests/promoted-pr-{n}` → default branch | `tests/e2e/` | "Promote E2E tests from merged PR #N" — tests join the standard CI matrix. |

**Idempotency.** `PrWorkflowRun.followUpPrNumber` is set on the first
successful promotion. Re-runs (`@bot rerun-tests`), late merge events
and webhook retries all observe the populated field and no-op.

**Conflict policy.** If `tests/e2e/<file>` already exists, the bot
inserts a numeric suffix before the first dot:
`login.spec.ts → login_2.spec.ts → login_3.spec.ts`. The chosen final
paths are listed in the follow-up PR description so reviewers see
exactly what landed.

**Failure handling.** Workspace / git / API failures degrade to a
"❌ Promotion failed — …" comment on the parent PR. The parent run's
terminal status is never rolled back — promotion is best-effort.

**Teardown.** `ephemeral` and `commit-to-pr` suites are deleted on PR
close (no longer needed). `offer-as-pr` / `promote-on-merge` suites are
kept so the dashboard can correlate the parent run with its follow-up
PR. A nightly `PromotedSuiteGarbageCollector`
(`@Scheduled` cron, default 03:17 server-time, configurable via
`prworkflow.e2e.promotion.gc-cron`) removes the in-DB suite rows once
the owning run finished more than `prworkflow.e2e.promotion.retention`
ago (default `P30D`). The promoted-PR link on `PrWorkflowRun` is
preserved so the dashboard keeps showing "promoted as PR #N".

**Security note.** Promoted tests run in the standard CI on the
default branch and may need manual secret review — selectors and
fixtures are LLM-generated and could leak environment-specific URLs,
test usernames or recorded responses. Treat each follow-up PR like
any other contribution: code review, branch-protection rules, secret
scanners all apply unchanged.

**Recipe.** A laptop-runnable walkthrough that exercises each mode
against a real Gitea instance lives at
[`systemtest/README-suite-promotion.md`](../systemtest/README-suite-promotion.md).

## Coming next (post-wave 2)

Already shipped in wave 2 / iterations 1 – 4:

- ✅ `TestPlannerAgent`, `TestAuthorAgent`, `TestRunnerAgent` driving
  the Playwright runner via the dedicated `E2eAgentRunner`.
- ✅ Built-in tools under category `PR_WORKFLOW`: `pr-test-write`,
  `pr-test-run`, `preview-url`, `preview-status`, `attach-artifact`.
- ✅ Slash commands `@bot rerun-tests` and
  `@bot regenerate-tests [feedback]` (`E2eTestSlashCommandHandler`)
  wired through all four provider webhook handlers, with the trailing
  free-text threaded into the planner's user message via
  `PrWorkflowContext.hints`.
- ✅ `Full-stack QA` seeded workflow configuration (Flyway `V18`,
  H2 + PostgreSQL) — opt-in, never auto-attached.
- ✅ `SuiteLifecycleMode.EPHEMERAL`-aware teardown in
  `E2eTestPrCloseHandler` (broadcasts `DeploymentStrategy.teardown`
  per registered strategy, cleans the sandbox, deletes ephemeral
  suites).
- ✅ Sample app under `systemtest/sample-e2e-app/` +
  `systemtest/docker-compose-e2e-sample.yml`.
- ✅ Per-provider native `attachPullRequestArtifact` overrides:
   * **GitLab** — multipart POST to `/projects/:id/uploads`,
     embeds the returned `markdown` link.
   * **Gitea** — multipart POST to
     `/repos/:o/:r/issues/:n/assets`, links to
     `browser_download_url`.
   * **Bitbucket Cloud** — multipart POST to
     `/repositories/:ws/:r/downloads`, links to the resulting
     `/downloads/<name>` URL.
   * **GitHub** — keeps the inline default. GitHub has no
     first-class PR-comment attachment API outside Releases assets,
     and uploading every E2E artifact to a Release would create
     noisy entries, so the default is the recommended path.
  All three native overrides keep using
  {@link org.remus.giteabot.repository.ArtifactCommentRenderer}
  for inlineable images / text (better reviewer UX) and only switch
  to the native upload for the renderer's `SUMMARY_ONLY` fallback
  (large or binary non-image artifacts). Upload failures degrade
  silently back to the summary comment.

Deferred (post-wave 2):

- Composite WireMock-based end-to-end integration test that exercises
  the full planner → deploy → author → run → comment pipeline against
  the sample app in one go. The current per-component tests
  (`E2ETestWorkflowTest`, `PlaywrightTestSuiteRunnerTest`,
  `TestPlannerAgentTest`, `TestAuthorAgentTest`, `TestRunnerAgentTest`,
  the per-provider `ArtifactUploadTest`s, and
  `PrWorkflowToolExecutorTest`) cover the building blocks; the
  composite test would only add a smoke check.
- Repo-file-access tools (`cat`, `rg`, `tree`, `get-issue`) for the
  planner — requires plugging into `AgentToolRouter` and a
  source-workspace mount.
- Optional Playwright MCP server integration as an alternative to the
  local Node-based runner.












