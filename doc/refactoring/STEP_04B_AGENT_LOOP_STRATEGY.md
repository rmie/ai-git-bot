# Step 4b — Vollständiger `AgentLoop` mit Strategy-Pattern

> **Status: ✅ erledigt.** Der generische `AgentLoop` lebt unter
> `org.remus.giteabot.agent.loop` und wird von beiden Agents genutzt
> (`CodingAgentStrategy` und `WriterAgentStrategy`). Charakterisierungstests
> in `AgentLoopTest` und `CodingAgentStrategyTest` (6 neue Tests) pinnen die
> drei dokumentierten Pfade (Multi-Round-Validation-Retry,
> `IGNORE_MCP_AFTER_VALIDATION_SUCCESS`, File-only-Erfolg) sowie die
> History/Session-Synchronisation des Loops fest.

> Ergänzungs-Issue zur Migrationsreihenfolge. Schritt 4 wurde im ersten Durchgang
> bewusst nur teilweise umgesetzt (`MAX_TOOL_ROUNDS` konfigurierbar, Tool-Dispatch
> via `AgentToolRouter`). Der eigentliche generische Loop ist hier separat
> erfasst, weil er den größten Verhaltensraum berührt und intensive Test-
> Charakterisierung braucht.

## Ziel

Beide Agenten (`IssueImplementationService.runToolImplementationLoop` und
`WriterAgentService.runWriterLoop`) durch einen einzigen, generischen
`AgentLoop<P>` ersetzen, der die folgenden Aufgaben kapselt:

- Round-Counter inkl. budget-Aware Abbruch (`maxRounds`, `maxContextRounds`,
  `maxValidationRetries`).
- History/Session-Synchronisation (heute parallel über
  `history.add(AiMessage…)` UND `sessionService.addMessage(session, …)`).
- AI-Aufruf inkl. Token-Budget-Übergabe.
- Plan-Parsing und Plan-Klassifikation (Context-Request / Tool-Run / Final).
- Branch-Switch-Vorabbehandlung (heute via `BranchSwitcher`, bereits extrahiert).
- Tool-Dispatch (heute via `AgentToolRouter`, bereits extrahiert).
- Übergabe des finalen Plans an die agentenspezifische Domänen-Aktion
  (Coding: `commit + push + PR`, Writer: `createIssue`).

## Vorgeschlagene API

```java
public final class AgentLoop<P> {
    public AgentLoop(AiClient ai,
                     AgentSessionService sessions,
                     AgentToolRouter toolRouter,
                     BranchSwitcher branchSwitcher,
                     AgentBudget budget,
                     AgentStrategy<P> strategy) { … }

    public LoopOutcome<P> run(AgentSession session,
                              AgentRunContext ctx,
                              String initialUserMessage);
}

public interface AgentStrategy<P> {
    String systemPrompt(AgentSession session);
    P parsePlan(String aiResponse);
    StepDecision interpret(AgentRunContext ctx, P plan);   // CONTEXT / FINAL / RETRY
    String contextFeedback(List<ToolResult> results);
    AgentToolRouter.Mode toolMode();
}

public record AgentRunContext(String owner, String repo, Long issueNumber,
                              Path workspaceDir, String baseBranch) { … }

public record AgentBudget(int maxRounds, int maxContextRounds,
                          int maxValidationRetries, int maxTokensPerCall) { … }
```

`StepDecision` modelliert die heute verstreuten If-Zweige:

```java
sealed interface StepDecision {
    record RequestContext(List<ImplementationPlan.ToolRequest> tools) implements StepDecision {}
    record RunTools(List<ImplementationPlan.ToolRequest> tools) implements StepDecision {}
    record AskUser(String comment, AgentSession.AgentSessionStatus newStatus) implements StepDecision {}
    record Finalize(Object payload) implements StepDecision {}
    record Fail(String reason) implements StepDecision {}
}
```

## Implementierungsschritte

1. **Charakterisierungstests**: Aktuelle Loop-Verzweigungen mit Mockito-Sequenzen
   (Reihenfolge der `aiClient.chat`-Antworten) als Snapshot fixieren —
   insbesondere `IssueImplementationServiceTest` um folgende Pfade ergänzen:
   - Multi-Round-Validation-Retry (heute `attempt--`-Hack).
   - `IGNORE_MCP_AFTER_VALIDATION_SUCCESS`-Policy.
   - File-only-Erfolg ohne Validation-Tool.
2. `AgentLoop`, `AgentStrategy`, `StepDecision`, `AgentRunContext`,
   `AgentBudget` anlegen.
3. `WriterAgentStrategy implements AgentStrategy<WriterPlan>` extrahieren —
   weil Writer-Loop simpler ist, dort starten.
4. `IssueImplementationServiceTest` muss erst alle Charakterisierungstests
   bestehen, bevor die `runToolImplementationLoop` durch
   `CodingAgentStrategy` ersetzt wird.
5. `attempt--`-Hack durch separates Budget für `contextRounds` ersetzen
   (kein Zähler-Verschachteln mehr).
6. Drei Loop-spezifische Counters (`attempt`, `toolRounds`, `fileRequestRounds`)
   in `AgentBudget` zusammenfassen.

## Risiken & Mitigation

| Risiko | Mitigation |
|---|---|
| Subtile Verhaltensänderung in `attempt--`-Logik | Vorab dedizierte Unit-Tests für drei dokumentierte Übergänge. |
| Status-Übergänge (`UPDATING` ↔ `IN_PROGRESS` ↔ `PR_CREATED`) verschoben | Eigene Tests pro Übergang, nicht nur End-Zustand. |
| MCP-Sonderpolicy aus dem Loop verschwindet | Policy-Hook im `AgentBudget` oder als `EvaluationPolicy`-Strategy injizieren. |
| Tests `IssueImplementationServiceTest` brechen wegen Helper-Mock-Setups | Strategy hinter Default-Konstruktor mit Bot-Service-Standardinstanz. |

## Akzeptanzkriterien

- Beide Agents enthalten **keinen** eigenen Loop-Code mehr; nur noch
  `agentLoop.run(session, ctx, initialPrompt).onFinal(this::createPullRequest|::createIssue)`.
- Komplexitätsmetriken (cyclomatic) für `IssueImplementationService` < 30 (heute
  weit über 60) und `WriterAgentService` < 20.
- Bestehende Test-Suite (428 Tests) bleibt grün. **Plus** mind. 6 neue
  Charakterisierungstests, alle vorab eingecheckt.
- Konfig: `agent.budget.max-rounds`, `agent.budget.max-context-rounds`,
  `agent.budget.max-validation-retries` ersetzen die heutigen Streufelder.

