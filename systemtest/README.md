# `systemtest/` — Hands-on scenarios for AI-Git-Bot features

This folder collects Docker-Compose scenarios that let you exercise a
**single feature of AI-Git-Bot end-to-end on a laptop**, without
touching a real production Git provider, AI provider or platform
service. Each scenario boots only what that feature needs, and most
share the `ai-git-bot-e2e` Docker network so they can be combined.

Use this file as the index: pick the feature you want to try, follow
the linked README, then come back here for the next one.

---

## 1. Agentic PR workflows (the `e2e-test` workflow + suite promotion)

The headline feature group. Each scenario plugs into a different
`DeploymentStrategy` (see
[`../doc/agentic-workflows/README.md`](../doc/agentic-workflows/README.md))
and lets you watch a real PR run through `plan → deploy → author →
run → comment` against the bot.

| Scenario | Walkthrough | Compose file | What it demonstrates |
|---|---|---|---|
| **Sample E2E target app** | (no dedicated README — pair with a deployment-target scenario below) | [`docker-compose-e2e-sample.yml`](./docker-compose-e2e-sample.yml) | Boots [`sample-e2e-app/`](./sample-e2e-app/) on port `3030`. Used as the "preview deployment" by every other E2E scenario. |
| **`MCP` deployment** | [`README-mcp-deployment.md`](./README-mcp-deployment.md) | [`docker-compose-mcp-deployment.yml`](./docker-compose-mcp-deployment.yml) | A ~80-line Node MCP server exposing `platform__deploy_preview` / `platform__preview_status` / `platform__teardown_preview`. Exercises `MCPDeploymentStrategy` save-time + runtime whitelist enforcement. User story: [`MCP_DEPLOYMENT_USER_STORY.md`](../doc/agentic-workflows/MCP_DEPLOYMENT_USER_STORY.md). |
| **`CI_ACTION` deployment** | [`README-ci-action.md`](./README-ci-action.md) | [`docker-compose-ci-action.yml`](./docker-compose-ci-action.yml) | Mock GitHub-Actions–style dispatch+poll REST API on port `8091`. Exercises `CiActionTriggerStrategy` + `CiActionPoller`, the `WAITING_DEPLOY → READY` / `FAILED` transitions, and `@bot rerun-tests` re-dispatch semantics. User story: [`CI_ACTION_DEPLOYMENT_USER_STORY.md`](../doc/agentic-workflows/CI_ACTION_DEPLOYMENT_USER_STORY.md). |
| **Suite promotion (M7 modes)** | [`README-suite-promotion.md`](./README-suite-promotion.md) | reuses [`docker-compose-local-gitea.yml`](./docker-compose-local-gitea.yml) + [`docker-compose-e2e-sample.yml`](./docker-compose-e2e-sample.yml) | All four `suiteLifecycle` modes (`ephemeral` / `commit-to-pr` / `offer-as-pr` / `promote-on-merge`) plus idempotency, conflict policy, push-failure degradation, and the nightly `PromotedSuiteGarbageCollector`. User story: [`SUITE_PROMOTION_USER_STORY.md`](../doc/agentic-workflows/SUITE_PROMOTION_USER_STORY.md). |

> **`WEBHOOK` and `STATIC` deployment strategies do not have a
> dedicated scenario yet** — they need infrastructure that varies too
> much per team (Jenkins URL, Vercel project, …). For `WEBHOOK` see the
> per-CI recipes in [`../doc/PR_WORKFLOWS_WEBHOOK_RECIPES.md`](../doc/PR_WORKFLOWS_WEBHOOK_RECIPES.md);
> for `STATIC` point a deployment target at any reachable preview URL.

### Typical "agentic workflows" warm-up

```bash
# Terminal A — sample app (target of all preview URLs)
docker compose -f systemtest/docker-compose-e2e-sample.yml up --build

# Terminal B — local Gitea (host for the parent PR)
docker compose -f systemtest/docker-compose-local-gitea.yml up

# Terminal C — pick one deployment-target scenario, e.g. MCP
docker compose -f systemtest/docker-compose-mcp-deployment.yml up --build

# Terminal D — the bot itself (host build is fastest)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Then open the bot UI, register the deployment target as documented in
the per-scenario README, and open a PR on the Gitea side.

> 📸 *Screenshot placeholder — Deployment target form in the admin UI*
> `doc/screenshots/prworkflow/deployment-target-form.png` (TODO)

---

## 2. Local Git provider stacks

Bring up a real, self-contained Git provider so the bot can talk to it
end-to-end. Useful for reviewing webhook delivery, MR/PR comment
formatting, slash commands, and the four-provider matrix in general.

| Scenario | Compose file | Notes |
|---|---|---|
| **Local Gitea** | [`docker-compose-local-gitea.yml`](./docker-compose-local-gitea.yml) | Gitea on `:3000` with persistent data under [`gitea/`](./gitea/). Pre-seeded with an `acme/demo` repo on first boot. Used by the suite-promotion walkthrough and by ad-hoc PR-review tests. |
| **Local GitLab** | [`docker-compose-local-gitlab.yml`](./docker-compose-local-gitlab.yml) | Self-managed GitLab CE for exercising the `GitLabApiClient`, MR webhooks and the `attachPullRequestArtifact` upload path. Takes ~3 min on first boot. |

> For real GitHub / GitHub Enterprise / Bitbucket Cloud testing there
> is no compose scenario — point the bot at a sandbox repo on the
> hosted service. The per-provider setup pages
> ([Gitea](../doc/GITEA_SETUP.md), [GitHub](../doc/GITHUB_SETUP.md),
> [GitLab](../doc/GITLAB_SETUP.md), [Bitbucket](../doc/BITBUCKET_SETUP.md))
> document credentials and webhook configuration.

---

## 3. Local AI providers

Run the bot fully offline against a local LLM runtime — useful for
load-testing the agent loop without burning tokens, and for the
[Using Ollama](../doc/OLLAMA.md) / [Using llama.cpp](../doc/LLAMACPP.md)
documentation pages.

| Scenario | Compose file | Notes |
|---|---|---|
| **Ollama** | [`docker-compose-ollama.yml`](./docker-compose-ollama.yml) | Ollama daemon on `:11434`. Pull a model first with `docker exec ai-git-bot-ollama ollama pull llama3`. |
| **llama.cpp** | [`docker-compose-llamacpp.yml`](./docker-compose-llamacpp.yml) | llama.cpp server on `:8081`. Bring your own GGUF model — see [`../doc/LLAMACPP.md`](../doc/LLAMACPP.md) for the volume mount. |

---

## 4. MCP scenarios

The MCP integration is documented in
[`../doc/MCP_SERVER_HANDLING.md`](../doc/MCP_SERVER_HANDLING.md). Two
scenarios here let you watch tool discovery and tool execution flow
through `McpOrchestrationService` without standing up a real platform.

| Scenario | Walkthrough | Compose / notes |
|---|---|---|
| **Remote GitHub Copilot MCP** | [`README-mcp-github.md`](./README-mcp-github.md) | No local server — point an MCP configuration at `https://api.githubcopilot.com/mcp/` with a valid Copilot OAuth token. Demonstrates remote MCP discovery and tool execution. |
| **Local MCP deployment server** | [`README-mcp-deployment.md`](./README-mcp-deployment.md) (cross-listed under "Agentic PR workflows") | [`sample-mcp-deploy-server/`](./sample-mcp-deploy-server/) — same Node MCP server used by the `MCP` deployment-strategy scenario, exercising tool whitelisting + execution end-to-end. |

---

## 5. Sample apps used by the scenarios

| Folder | Used by | What it is |
|---|---|---|
| [`sample-e2e-app/`](./sample-e2e-app/) | All "Agentic PR workflows" scenarios | Tiny Node login form — the bot's planner targets it, the author writes Playwright specs against it. |
| [`sample-mcp-deploy-server/`](./sample-mcp-deploy-server/) | `MCP` deployment scenario | Node MCP server (streamable HTTP transport) simulating a platform's deploy/status/teardown tools. |
| [`sample-ci-action-server/`](./sample-ci-action-server/) | `CI_ACTION` deployment scenario | Node mock of the GitHub Actions REST API surface the bot actually calls. |

---

## Quick reference — which scenario do I run for which doc?

| Doc page | Scenario(s) |
|---|---|
| [`../doc/agentic-workflows/README.md`](../doc/agentic-workflows/README.md) | All "Agentic PR workflows" scenarios above. |
| [`../doc/PR_WORKFLOWS_E2E.md`](../doc/PR_WORKFLOWS_E2E.md) | `docker-compose-e2e-sample.yml` + any deployment-target scenario. |
| [`../doc/PR_WORKFLOWS_CI_ACTIONS.md`](../doc/PR_WORKFLOWS_CI_ACTIONS.md) | `README-ci-action.md`. |
| [`../doc/PR_WORKFLOWS_WEBHOOK_RECIPES.md`](../doc/PR_WORKFLOWS_WEBHOOK_RECIPES.md) | No compose — per-CI recipes against your own infra. |
| [`../doc/MCP_SERVER_HANDLING.md`](../doc/MCP_SERVER_HANDLING.md) | `README-mcp-github.md` (remote) + `README-mcp-deployment.md` (local). |
| [`../doc/OLLAMA.md`](../doc/OLLAMA.md) | `docker-compose-ollama.yml`. |
| [`../doc/LLAMACPP.md`](../doc/LLAMACPP.md) | `docker-compose-llamacpp.yml`. |
| [`../doc/GITEA_SETUP.md`](../doc/GITEA_SETUP.md) | `docker-compose-local-gitea.yml`. |
| [`../doc/GITLAB_SETUP.md`](../doc/GITLAB_SETUP.md) | `docker-compose-local-gitlab.yml`. |

---

## Conventions across scenarios

- Every scenario joins (or is compatible with) the shared
  `ai-git-bot-e2e` Docker network so the bot can reach them on stable
  hostnames (`sample-e2e-app`, `sample-mcp-deploy`,
  `sample-ci-action-server`, …).
- All sample servers expose a `GET /healthz` endpoint suitable for
  Docker healthchecks and the bot's `preview-status` tool.
- All scenarios are **stateful in-memory only** — `docker compose down
  -v` cleans up everything; restart for a fresh slate.
- All scenarios are designed to run *without* network access to the
  real Git provider / AI provider / platform — the only outbound
  traffic is what your local LLM or local Git host needs.

