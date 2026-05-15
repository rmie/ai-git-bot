# Agent-Architektur — Refactoring-Roadmap

Dieses Verzeichnis bündelt die geplanten Folgeschritte aus dem
Architektur-Review der Agent-Implementierung
(`IssueImplementationService` und `WriterAgentService`).

## Status

| Schritt | Status | Beschreibung |
|---|---|---|
| 1 | ✅ erledigt | Shared Utils (`BranchRefs`, `BranchSwitcher`, `ToolFailures`, `McpTools`) |
| 2 | ✅ erledigt | `ObjectMapper`-Singleton via `AgentJackson` |
| 3 | ✅ erledigt | `AgentToolRouter` + `ToolKind`/`AgentTool`/`ToolCallContext` |
| 4 | ✅ erledigt | Konfigurierbare Writer-Limits + Tool-Dispatch via Router |
| 4b | ✅ erledigt | [Vollständiger AgentLoop mit Strategy-Pattern](STEP_04B_AGENT_LOOP_STRATEGY.md) |
| 5 | ✅ erledigt | [JSON-Schema-Validation für Agent-Antworten](STEP_05_JSON_SCHEMA_VALIDATION.md) |
| 6 | ⬜ offen | [Provider-natives Function Calling](STEP_06_NATIVE_FUNCTION_CALLING.md) |
| 7 | ⬜ offen | [Plan-Persistenz, Budget-Konsolidierung, Critic-Step](STEP_07_PLAN_BUDGET_CRITIC.md) |

## Reihenfolge

`4b → 5 → 6 → 7` ist die empfohlene Bearbeitungsreihenfolge. Schritt 7 ist in
sich gegliedert (7.1 / 7.2 / 7.3) und kann unabhängig zwischen den anderen
Schritten eingeschoben werden.

## Verhalten / Tests

Die gesamte Test-Suite (zuletzt **444 Tests** inkl. der 6 in Schritt 4b
ergänzten Charakterisierungstests in `AgentLoopTest` und
`CodingAgentStrategyTest` sowie der 8 Snapshot-/Validator-Tests aus
Schritt 5 in `AgentSchemaValidatorTest`) wurde nach jedem der umgesetzten
Schritte 1–5 grün gehalten. Charakterisierungstests vor invasiven Änderungen
sind im jeweiligen Issue benannt.

