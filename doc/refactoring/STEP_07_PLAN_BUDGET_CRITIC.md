# Step 7 — Plan-Persistenz, Budget-Konsolidierung, Critic/Reflection-Step

Dieses Issue bündelt drei kleine, voneinander unabhängige Verbesserungen, die
zusammen die Wartbarkeit und Qualität des Coding-Agents heben.

---

## 7.1 Plan-Persistenz am `AgentSession`-Modell

### Problem

`IssueImplementationService.getLastPlanFromSession` (heute Z. 906 ff.) iteriert
rückwärts durch die Conversation-History und re-parst jede `assistant`-Message
mit `AiResponseParser.parseAiResponse`, bis ein Plan zurückkommt. Das ist:
- Performance-relevant bei langen Sessions.
- Quelle von Inkonsistenzen, wenn das Schema zwischen Runs wechselt.
- Schlecht testbar: der "letzte Plan" hängt vom Zustand der DB ab.

### Lösung

Neue Spalten in `agent_session`:

| Spalte | Typ | Inhalt |
|---|---|---|
| `last_plan_summary` | `VARCHAR(2048)` | `plan.summary` |
| `last_plan_json`    | `CLOB`          | Roh-JSON des letzten geparsten Plans |
| `last_plan_at`      | `TIMESTAMP`     | Zeitpunkt |

Migration (Flyway):
```sql
ALTER TABLE agent_session
  ADD COLUMN last_plan_summary VARCHAR(2048),
  ADD COLUMN last_plan_json    CLOB,
  ADD COLUMN last_plan_at      TIMESTAMP;
```

`AgentSessionService.recordPlan(session, plan, rawJson)` neu, wird vom
`AgentLoop` (Schritt 4b) nach jedem erfolgreichen Plan-Parse aufgerufen.
`getLastPlanFromSession` entfällt, `IssueImplementationService.handleIssueAssigned`
liest direkt `session.getLastPlanJson()` und parst einmal.

### Akzeptanzkriterien

- Migration läuft auf bestehender H2-/Postgres-Konfig idempotent.
- PR-Body und Comment-Formatierung sind byte-gleich zum heutigen Verhalten.
- Mind. 1 Test prüft, dass nach drei Loop-Runden tatsächlich der **letzte**
  Plan im Feld steht.

---

## 7.2 Budget-Konsolidierung

### Problem

Heute existieren mehrere überlappende Counter, deren Semantik nur durch das
Lesen von Code+Kommentaren erschlossen werden kann:

| Limit | Quelle | Wirkung |
|---|---|---|
| `validation.maxRetries` | `AgentConfigProperties` | äußere Implementation-Schleife |
| `validation.maxToolExecutions` | `AgentConfigProperties` | innere Tool-Round-Schleife |
| `MAX_CONTEXT_TOOL_REQUESTS = 5` | `IssueImplementationService` | `executeRequestedContextTools` |
| `writer.maxToolRounds = 5` | `AgentConfigProperties.WriterConfig` (Schritt 4) | Writer-Loop |
| `writer.maxInitialTreeFiles = 100` | dito | Tree-Snapshot Größe |
| File-Request-Round-Counter `< 3` | hartkodiert in `runToolImplementationLoop` | Context-Phase |
| `maxTokens` | `AgentConfigProperties` | LLM-Aufruf |

Plus: der `attempt--`-Hack (Z. 318 in `IssueImplementationService`) zählt
absichtlich Context-Anfragen NICHT als Implementation-Versuch.

### Lösung

Eine Klasse `AgentBudget` als Sub-Konfig:

```java
@Data
public static class BudgetConfig {
    private int maxRounds = 10;           // entspricht max(validation.maxToolExecutions, writer.maxToolRounds)
    private int maxContextRounds = 3;     // ersetzt File-Request-Round-Counter
    private int maxValidationRetries = 3; // ersetzt validation.maxRetries
    private int maxContextToolRequestsPerRound = 5; // ersetzt MAX_CONTEXT_TOOL_REQUESTS
    private int maxTokensPerCall = 16384; // ersetzt agentConfig.getMaxTokens
}
```

Die alten Felder bleiben `@Deprecated` mit Default-Werten, die das
`BudgetConfig` als Quelle der Wahrheit ergänzen — Migration über
`@PostConstruct`-Mapping in `AgentConfigProperties`.

### Akzeptanzkriterien

- Default-Verhalten zu 100% identisch (gemessen über 428 Tests + neue
  Round-Counter-Tests).
- Heute hartkodierte Werte (`5`, `3`, `100`) sind nicht mehr im Code.
- `attempt--`-Hack durch separates `contextRounds`-Counter ersetzt.

---

## 7.3 Critic / Reflection-Step (optional, Coding-Agent)

### Problem

Der Coding-Agent committet, sobald Validation-Tools grün sind und der Workspace
Änderungen aufweist. Es gibt **keine** zweite Prüfung, ob die Änderung das
ursprüngliche Issue tatsächlich umsetzt. Self-Refine / Reflexion-Pattern (Shinn
et al. 2023) zeigt, dass ein zusätzlicher Critic-Call die Qualität deutlich
verbessert — gerade bei mehrdeutigen Issues.

### Lösung

Neuer optionaler Schritt nach erfolgreicher Validation, vor `commitAndPush`:

```java
ReflectionResult reflect = criticAgent.review(
    issue, plan, workspaceService.diffStat(workspaceDir));
switch (reflect.outcome()) {
    case APPROVE  -> proceedToCommit();
    case ITERATE  -> { feedback = reflect.feedback(); continue; }   // wie Validation-Failure
    case ABORT    -> failWithComment(reflect.feedback());
}
```

`CriticAgent` ist eine eigene `AgentStrategy<ReflectionResult>` (vgl.
Schritt 4b), nutzt denselben `AiClient` aber ein eigenes System-Prompt
(`prompts/critic.md`).

### Konfig

```yaml
agent:
  critic:
    enabled: false                # default: aus
    max-iterations: 1
    require-approval-for: [LARGE_DIFF]   # optionale Trigger
```

### Akzeptanzkriterien

- Bei `enabled=false` wird **kein** zusätzlicher LLM-Call ausgelöst (assert in
  Test).
- Bei `enabled=true` wird der Iterations-Pfad dem Validation-Retry-Pfad
  gleichgestellt (zählt auf `maxRounds` ein).
- `agent.critic.outcome_total{outcome=approve|iterate|abort}` Metrik vorhanden.

### Aufwand

- 7.1: 1 PT
- 7.2: 1–2 PT (Migration, Tests)
- 7.3: 2 PT plus Prompt-Engineering

---
## Status: ✅ implementiert (2026-05)
### Umgesetzt
**7.1 Plan-Persistenz**
- Migrationen `V11__agent_session_last_plan.sql` (h2 `CLOB`, postgresql `TEXT`).
- `AgentSession`: neue Felder `lastPlanSummary` (VARCHAR 2048), `lastPlanJson`
  (`@Lob`), `lastPlanAt` (`Instant`).
- `AgentSessionService.recordPlan(session, summary, rawJson)` schreibt
  write-through nach jedem erfolgreichen Parse.
- `CodingAgentStrategy.step` ruft `recordPlan` direkt nach dem `parseAiResponse`
  auf — kein Re-Parse der History mehr.
- `IssueImplementationService.getLastPlanFromSession` liest jetzt zuerst
  `session.getLastPlanJson()` (O(1)). History-Walk bleibt als Fallback für
  Sessions, die vor V11 angelegt wurden.
**7.2 Budget-Konsolidierung**
- Neue Klasse `AgentConfigProperties.BudgetConfig` (Defaults: maxRounds=10,
  maxContextRounds=3, maxValidationRetries=3, maxContextToolRequestsPerRound=5,
  maxTokensPerCall=16384).
- `@PostConstruct applyLegacyBudgetDefaults()` bridged die deprecated Felder
  `agent.max-tokens` und `agent.validation.max-retries` automatisch in die
  neue `BudgetConfig`, solange diese auf den Built-in-Defaults steht.
- `IssueImplementationService` und `CodingAgentStrategy` lesen ausschließlich
  aus `BudgetConfig`. Hardcodierte `MAX_CONTEXT_TOOL_REQUESTS = 5` und
  `fileRequestRounds < 3` entfernt.
- `WriterAgentService` benutzt `BudgetConfig.getMaxTokensPerCall()`.
**7.3 Critic / Reflection-Step**
- Neuer Strategy-unabhängiger Helper `CriticAgent` mit Outcome-Enum
  `APPROVE | ITERATE | ABORT | SKIPPED`.
- Prompt: `src/main/resources/prompts/critic.md`.
- Konfiguration: `agent.critic.enabled` (default `false`),
  `agent.critic.max-iterations`, `agent.critic.require-approval-for`.
- Eingehängt in `IssueImplementationService.handleIssueAssigned` direkt nach
  dem Implementation-Loop und vor `commitAndPush`.
- Metrik `agent.critic.outcome_total{outcome}` über
  `AgentMetrics.recordCriticOutcome` (skipped/approve/iterate/abort/error/parse_error).
- Default `enabled=false` sorgt für **null** zusätzliche LLM-Calls in der
  Standardkonfiguration.
### Tests (476 grün, +12 ggü. Step 6)
- `CriticAgentTest` (5): disabled-no-call, JSON parsing, code-fence stripping,
  unparseable fail-open, exception fail-open.
- `AgentConfigPropertiesBudgetTest` (5): Defaults, Legacy-Migration,
  Override-Semantik, CriticConfig-Defaults.
- `AgentSessionServicePlanPersistenceTest` (2): write-through der drei Spalten,
  Overwrite-Verhalten bei mehrfachem `recordPlan`.
### Bekannte offene Punkte / nächste Schritte
- `CriticConfig.requireApprovalFor` ist heute rein informativ — sobald
  `LARGE_DIFF`-Heuristik (z.B. >300 changed lines) eingebaut ist, kann der
  Critic gezielt bei großen Änderungen erzwungen werden.
- `ITERATE` führt aktuell zu einer Failure-Notiz mit Bitte um `@bot`-Mention
  statt einem automatischen Re-Run. Volle Schleife ("Critic-Feedback wieder
  in den Loop einspeisen") ist bewusst zurückgestellt, damit das Verhalten
  konservativ bleibt.
