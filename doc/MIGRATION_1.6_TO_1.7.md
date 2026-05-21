# Migration guide — AI-Git-Bot 1.6 → 1.7

> **Target audience:** operators upgrading an existing 1.6.x deployment to
> the 1.7.0 GA release that bundles the seven-milestone "PR-review
> agentic workflows" track (M1 – M7).

This release adds two big surfaces — pluggable **PR workflows** and
pluggable **deployment targets** — and a new opt-in **E2E test
workflow** built on top of them. **Every change is additive:** existing
review bots keep working unchanged, no behaviour flips on without
operator action, and no Flyway migration drops or rewrites pre-1.7
data.

If you are upgrading from 1.0 / 1.1, also read
[`MIGRATION_1.0_TO_1.1.md`](./MIGRATION_1.0_TO_1.1.md) first.

---

## TL;DR — for impatient operators

1. **Drop in the new 1.7.0 JAR / image.** No code or config change is
   required to keep the legacy PR review running exactly as before.
2. **Pull the image once with the DB writable.** Flyway applies
   `V13 → V18` (one per milestone) on first boot.
3. **Opt in to new workflows per bot** when you want them (see § 3).
4. **Nothing else is mandatory.** All new properties have safe
   defaults.

Run the existing test suite (`mvn verify`) — it stays at **809 green**.

---

## 1. What's new at a glance

| Area | 1.6.x | 1.7.0 |
|---|---|---|
| PR review | Single hard-coded review path | First-class `ReviewWorkflow` plugged into a `PrWorkflowRegistry` (M1). Behaviour unchanged. |
| Workflow configurations | Implicit | Named, CRUD-managed configurations; bots pick one. `Default review` seeded automatically (M2). |
| Deployment targets | Not a concept | New `DeploymentStrategy` SPI with four built-in strategies — `WEBHOOK`, `STATIC`, `MCP`, `CI_ACTION` (M3 + M5 + M6). |
| Callback channel | — | HMAC-signed `/api/workflow-callback/{runId}/{secret}` endpoint, single-use secrets (M3). |
| E2E test workflow | — | `e2e-test` workflow generating + running Playwright suites against deployed previews (M4 wave 1 + wave 2). Opt-in via `Full-stack QA` seeded configuration. |
| Slash commands | `@bot fix … / write …` | `@bot rerun-tests` / `@bot regenerate-tests [feedback]` added (M4). |
| Suite promotion | — | `suiteLifecycle` param (`ephemeral` / `commit-to-pr` / `offer-as-pr` / `promote-on-merge`) plus nightly `PromotedSuiteGarbageCollector` (M7). |

---

## 2. Database migrations

All Flyway migrations are **additive**. No pre-1.7 table is dropped,
renamed or column-truncated.

| Version | Adds | Milestone |
|---|---|---|
| `V13` | `pr_workflow_runs`, `pr_workflow_steps` | M1 |
| `V14` / `V15` | `workflow_configurations` + `bots.workflow_configuration_id` (nullable FK) + `Default review` seed | M2 |
| `V16` | `deployment_targets` (+ supporting tables) | M3 |
| `V17` | `pr_test_suites`, `pr_test_cases`, `pr_workflow_runs.follow_up_pr_number` | M4 wave 1 |
| `V18` | `Full-stack QA` workflow-configuration seed (idempotent, H2 + PostgreSQL) | M4 wave 2 / it. 3 |

The seeded `Default review` (V15) and `Full-stack QA` (V18) rows are
guarded with `INSERT … WHERE NOT EXISTS` predicates — a second
deployment pointed at the same DB is a no-op.

**Rollback.** Each migration is forward-only in the Flyway sense; if
you need to roll back the JAR, the new tables remain but stay unused
(the 1.6.x bot never reads them).

---

## 3. Opt-in steps per feature

### 3.1 Review workflow (M1)

Nothing to do. Your existing bots continue to run the legacy review
path, now under the `ReviewWorkflow` umbrella.

### 3.2 Workflow configurations (M2)

The `Default review` configuration is seeded and the `bots`
table gets a nullable `workflow_configuration_id`. Bots without a
configuration fall back to the global defaults (= 1.6.x behaviour).

To customise: **Admin → System settings → Workflow configurations**,
edit `Default review` or create a new one, then assign it on the bot
form.

### 3.3 Deployment targets (M3 / M5 / M6)

Create a deployment target only if you want to run the `e2e-test`
workflow or future deployment-aware workflows. Recipes:

- **`WEBHOOK`** — your existing Jenkins/TeamCity/script:
  [`WEBHOOK_DEPLOYMENT_USER_STORY.md`](./agentic-workflows/WEBHOOK_DEPLOYMENT_USER_STORY.md)
  and [`PR_WORKFLOWS_WEBHOOK_RECIPES.md`](./PR_WORKFLOWS_WEBHOOK_RECIPES.md).
- **`STATIC`** — Vercel/Netlify/Render-style review apps:
  [`STATIC_DEPLOYMENT_USER_STORY.md`](./agentic-workflows/STATIC_DEPLOYMENT_USER_STORY.md).
- **`MCP`** — internal platform MCP server:
  [`MCP_DEPLOYMENT_USER_STORY.md`](./agentic-workflows/MCP_DEPLOYMENT_USER_STORY.md)
  and [`MCP_SERVER_HANDLING.md` § 6](./MCP_SERVER_HANDLING.md).
- **`CI_ACTION`** — GitHub Actions / Gitea Actions / GitLab CI /
  Bitbucket Pipelines: [`PR_WORKFLOWS_CI_ACTIONS.md`](./PR_WORKFLOWS_CI_ACTIONS.md).

The HMAC callback endpoint is enabled automatically. Per-IP and
per-runId rate-limits are in place from M3.

### 3.4 E2E test workflow (M4)

The workflow ships **disabled** for every bot. Two opt-in steps:

1. Assign the `Full-stack QA` workflow configuration to the bot.
2. Assign a deployment target.

The full operator recipe — including slash commands and the sample
Node-app stack — lives at [`PR_WORKFLOWS_E2E.md`](./PR_WORKFLOWS_E2E.md).

### 3.5 Suite promotion (M7)

`suiteLifecycle` defaults to `ephemeral` (= 1.6.x behaviour: nothing
leaks past PR close). Switch to `offer-as-pr` / `promote-on-merge` /
`commit-to-pr` per workflow configuration in the admin UI. The bot
will reuse `WorkspaceService` + `RepositoryApiClient.createPullRequest`
to open follow-up PRs — no extra credentials needed beyond the ones
the bot already has.

New properties:

| Property | Default | Effect |
|---|---|---|
| `prworkflow.e2e.promotion.retention` | `P30D` | How long the in-DB `PrTestSuite` rows are kept after the follow-up PR is opened. Promoted-PR link on `PrWorkflowRun` is preserved indefinitely. |
| `prworkflow.e2e.promotion.gc-cron` | `0 17 3 * * *` | Spring cron expression for `PromotedSuiteGarbageCollector`. |

---

## 4. Configuration property changes

No 1.6.x property is removed or renamed. The new properties (all
optional, all with safe defaults) live under:

- `prworkflow.*` — workflow registry, callback endpoint, rate-limits.
- `prworkflow.e2e.*` — E2E workflow (model overrides, token budget,
  promotion retention/cron).
- `prworkflow.ci-action.poll-interval-ms` — CI_ACTION poll cadence
  (default 10 s).

Operator notes:

- The Flyway-managed `Default review` and `Full-stack QA` seeds are
  intentionally **opt-in** for bots — neither is auto-attached.
- `@EnableScheduling` is now active on the application bootstrap (M6).
  If you previously disabled scheduled tasks through Spring config
  for unrelated reasons, audit the new `CiActionPoller` and
  `PromotedSuiteGarbageCollector` beans.

---

## 5. Removed / replaced code (zero impact on existing deployments)

This is a clean, breaking-change-free release. The only behaviour
changes you might notice during normal operation:

- Per-bot review still uses the exact same prompts and tools — but
  the run now appears as a `PrWorkflowRun` row, visible in the new
  workflow-runs view of the admin dashboard.
- A bot configured with the `Full-stack QA` configuration **and** a
  deployment target will start running the `e2e-test` workflow on
  the next PR. Without one of the two, nothing changes.

---

## 6. Test bar

| Release | Tests | Notes |
|---|---|---|
| 1.6.x | 656 | Last pre-refactor baseline. |
| 1.7.0 (this release) | **809** | + M1 – M7 unit + integration tests, including `PromotedSuiteGarbageCollectorTest`. |

Run `mvn verify` after upgrade — every test should pass without
configuration changes.

---

## 7. Useful pointers

- Concept + architecture: [`agentic-workflows/CONCEPT_AND_ARCHITECTURE.md`](./agentic-workflows/CONCEPT_AND_ARCHITECTURE.md)
- Implementation plan: [`agentic-workflows/INTERNALS.md`](./agentic-workflows/INTERNALS.md)
- Per-feature user stories: `agentic-workflows/*_USER_STORY.md`
- Operator recipes: `PR_WORKFLOWS*.md`
- Systemtest walkthroughs: `../systemtest/README-*.md`

