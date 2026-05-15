# Step 6 — Provider-natives Function Calling

> **Status: ✅ erledigt** (Phase 1 + 2 — Infrastruktur, alle vier Provider,
> AgentLoop-Wiring, Telemetrie). Strategie-seitige Migration auf
> `step(ChatTurn)` mit echten Tool-Schemas folgt zusammen mit Schritt 7,
> sobald die Plan-Persistenz dafür den passenden Container hat. Solange
> kein konkreter Strategy auf {@code ToolingMode.NATIVE} umgestellt ist,
> entscheidet der Loop bei jedem Aufruf transparent für den Legacy-
> {@code chat()}-Pfad — die bestehende JSON-im-Prompt-Welt bleibt also
> voll funktionsfähig.

## Implementierungsnotizen (umgesetzt)

### Vertrag (`org.remus.giteabot.ai`)

- `ChatTurn(assistantText, toolCalls, stopReason)` mit `ChatTurn.text(...)`-Factory.
- `ToolCall(id, name, JsonNode args)` — Args jetzt als `JsonNode`, nicht mehr
  als String.
- `ToolDescriptor(name, description, JsonNode jsonSchema)` — Schemas aus
  Schritt 5 fließen direkt ein.
- `StopReason` (`END_TURN`, `TOOL_USE`, `MAX_TOKENS`, `OTHER`).
- `AiMessage` um `toolCalls` (Assistant-Turn) sowie `toolCallId` /
  `toolResult` (Tool-Turn) erweitert — alte Aufrufer setzen weiterhin nur
  `role`/`content`.
- `AiClient` bekommt
  `chatWithTools(history, msg, tools, sys, model, maxTokens)` als
  Default-Methode, die auf `chat(...)` delegiert, sowie
  `supportsNativeTools()` (Default `false`).
- `AiClientDelegateSupport` als gemeinsamer Helfer für alle Clients, die
  bei deaktiviertem Native-Mode auf `chat()` zurückfallen.

### Provider-Implementierungen

| Provider | `supportsNativeTools()` | `chatWithTools(...)` |
|---|---|---|
| Anthropic | `true` (default), Toggle | `tools[]` + `tool_use`/`tool_result` Content-Blocks |
| OpenAI | `true` (default), Toggle | `tools[]` + `tool_calls`/`tool_call_id` |
| Google Gemini | `true` (default), Toggle | `tools[].functionDeclarations` + `functionCall`/`functionResponse` |
| Ollama | `true` (default), Toggle | `tools[]` (OpenAI-kompatibel) auf `/api/chat` |
| llama.cpp | `false` (immer Legacy) | erbt Default → fällt automatisch auf `chat()` zurück |

Alle vier nativen Clients haben einen zusätzlichen Konstruktor-Parameter
`nativeToolsEnabled`, der `supportsNativeTools()` steuert. Der bisherige
Konstruktor existiert weiter (Default `true`), damit bestehende Tests/
Aufrufer kompatibel bleiben. Provider-Metadaten (`AnthropicProviderMetadata`,
`OpenAiProviderMetadata`, `GoogleAiProviderMetadata`,
`OllamaProviderMetadata`) reichen `!integration.isUseLegacyToolCalling()`
durch.

### Per-Integration-Toggle

`AiIntegration.useLegacyToolCalling` (Default `false`). Migration
`V10__use_legacy_tool_calling.sql` für H2 und PostgreSQL. Setzt der Operator
den Schalter, bekommt der Client `supportsNativeTools() == false` und
`chatWithTools` fällt automatisch auf `chat()` zurück — ohne Verlust von
bestehender Funktionalität. Im Admin-UI (`templates/ai-integrations/form.html`)
unter „Tool calling“ als Bootstrap-Switch mit Popover, das beide Modi
erklärt.

### AgentLoop-Wiring

- Neues `ToolingMode`-Enum (`LEGACY` / `NATIVE`).
- `AgentStrategy` bekommt drei Default-Methoden:
  - `preferredToolMode()` → Default `LEGACY`.
  - `toolDescriptors()` → Default `List.of()`.
  - `step(AgentRunContext, ChatTurn, int)` → Default delegiert auf
    `step(ctx, turn.assistantText(), round)`.
- `AgentLoop.resolveMode(strategy)` wählt pro Run einen der beiden Pfade:
  - `NATIVE`, wenn Strategy `NATIVE` will *und* Client
    `supportsNativeTools()` *und* mind. ein `ToolDescriptor` vorhanden ist.
  - sonst `LEGACY`. Der Loop loggt den Fallback-Grund auf DEBUG.
- Die alte Strategy-Schnittstelle (`step(ctx, String, round)`) bleibt
  unverändert. `CodingAgentStrategy` und `WriterAgentStrategy` implementieren
  weiter nur den Text-Pfad und fahren deshalb auf jedem konfigurierten
  Provider unverändert via `LEGACY`.

### Telemetrie (`org.remus.giteabot.agent.shared.AgentMetrics`)

Neuer Spring-Bean + Holder (analog `AgentSchemaValidator`/Holder) mit drei
Metriken:

| Metric | Tags | Wann |
|---|---|---|
| `agent.tool_call.mode_total` | `mode={native,legacy}`, `provider=<client class>` | Jede AI-Runde |
| `agent.tool_call.parse_failures_total` | `provider=<client class>` | Wird vom Parser-Pfad inkrementiert (Hook für Schritt 7) |
| `agent.tool_call.latency_seconds` | `mode`, `provider` | Wall-Clock pro Runde, gemessen um den `chat`/`chatWithTools`-Call |

Alle Tags werden lower-cased / null-safe normalisiert, fehlende Werte
werden als `unknown` getaggt. Verfügbar unter `/actuator/prometheus`.

### Tests

Ergebnis: **464 Tests grün** (zuvor 451). Neu:

- `AgentLoopToolModeTest` (4 Tests) — native vs. legacy Routing,
  Fallback-Pfade, `ChatTurn`-Forwarding.
- `AgentMetricsTest` (5 Tests) — Counter-/Timer-Verhalten, Holder-Delegation,
  Null-/Case-Normalisierung.
- `GoogleAiClientTest`: 2 zusätzliche Tests für `supportsNativeTools()`-
  Default und Override.
- `OllamaClientTest`: 2 zusätzliche Tests für `supportsNativeTools()`-
  Default und Override.

## Offene Punkte (Folge-PR)

1. `CodingAgentStrategy` und `WriterAgentStrategy` auf
   `step(AgentRunContext, ChatTurn, int)` migrieren und `toolDescriptors()`
   für die jeweils erlaubte Tool-Menge (file/validation/MCP-Tools mit
   Draft-2020-12-Schemas) liefern. Dann erst greift der Native-Pfad
   produktiv.
2. Native llama.cpp-Tool-Support, sobald wir auf den OpenAI-kompatiblen
   `/v1/chat/completions`-Endpoint umstellen. Aktuell bleibt llama.cpp
   bewusst auf dem `/completion`-Endpoint mit GBNF-Constraints.
3. Wenn ≥95 % der Bots stabil im Native-Mode laufen: Repair-Heuristiken
   (`repairTruncatedJson`, `sanitizeInvalidJsonEscapes`,
   `findLastCompleteRunTool`) deprecaten und entfernen.

---

## Kontext (ursprüngliche Planung)

Heute (post Schritt 1–5) ist der Tool-Vertrag rein textuell:
`AiClient.chat(history, userMessage, systemPrompt, modelOverride, maxTokens) → String`.
Tool-Calls werden im Antwort-String als JSON erwartet und durch
`AiResponseParser`/`WriterResponseParser` herausgeschnitten.

Alle drei großen Anbieter unterstützen mittlerweile native Function-/Tool-Calls:

| Provider | API |
|---|---|
| Anthropic | `tools[]` + `tool_use`/`tool_result` Content-Blocks |
| OpenAI | `tools[]` + `tool_calls` |
| Google Gemini | `tools[]` + `functionCall`/`functionResponse` |
| Ollama | `tools` (seit 0.3.x, OpenAI-kompatibel) |
| llama.cpp | `tools` (seit ggml-org/llama.cpp PR #6389) |

## Ziel

Pro Provider native Tool-Calls aktivieren, hinter einem Feature-Flag, so dass
JSON-im-Prompt der Fallback bleibt (für ältere Modelle / lokale Setups ohne
Tool-Support).

## Lieferumfang

1. **Neuer `AiClient`-Vertrag**:
   ```java
   ChatTurn chatWithTools(List<AiMessage> history,
                          List<ToolDescriptor> tools,
                          String systemPrompt,
                          Integer maxTokens);

   record ChatTurn(String assistantText,
                   List<ToolCall> toolCalls,
                   StopReason reason) {}

   record ToolCall(String id, String name, JsonNode args) {}

   record ToolDescriptor(String name,
                         String description,
                         JsonNode jsonSchema) {}
   ```
   Default-Methode delegiert auf das alte `chat(...)` und parst weiterhin JSON.
2. **Provider-Implementierungen**:
   - `AnthropicAiClient.chatWithTools` (tools-Block + tool_use → ToolCalls).
   - `OpenAiAiClient.chatWithTools` (function calling).
   - `GoogleAiClient.chatWithTools`.
   - `OllamaAiClient.chatWithTools` (mit Capability-Detection, sonst Fallback).
   - `LlamaCppAiClient.chatWithTools`.
3. **`ToolDescriptor`-Generierung** aus dem `AgentTool`-Registry (Schritt 3
   bereits vorbereitet) plus den Schemas aus Schritt 5.
4. **Loop-Anpassung** im `AgentLoop` (Schritt 4b): Wenn `chatWithTools` einen
   `ToolCall` zurückliefert, dispatch an `AgentToolRouter` und lege das Ergebnis
   als `tool_result`-Message in die History; sonst Final-Plan-Pfad wie heute.
5. **Feature-Flag** `agent.tooling.mode = LEGACY_JSON | NATIVE_TOOLS | AUTO`.
   `AUTO` aktiviert NATIVE_TOOLS, sobald der Provider Capability=true meldet.
6. **Test-Doubles**: `RecordingAiClient` mit programmierbaren `ChatTurn`-
   Sequenzen.

## Migrationsschritte

1. Schemas (Schritt 5) müssen produktiv sein.
2. Anthropic & OpenAI zuerst: einzelne A/B-Tests pro Bot (`agent.tooling.mode`
   in `bot_settings`-Tabelle).
3. Telemetrie:
   `agent.tool_call.mode_total{mode=…}`,
   `agent.tool_call.parse_failures_total`,
   `agent.tool_call.latency_seconds{provider=…}`.
4. Pilotumstellung pro Bot, mit Roll-back binnen Sekunden via Flag.
5. Wenn 95%+ Bots in NATIVE_TOOLS laufen, Repair-Heuristiken
   (`repairTruncatedJson`, `sanitizeInvalidJsonEscapes`,
   `findLastCompleteRunTool`) deprecaten.

## Designentscheidungen

- **History-Format einheitlich provider-agnostisch**: `AiMessage` bekommt eine
  optionale `toolCalls`/`toolResults`-Liste; provider-spezifische Mappings nur
  in den Clients.
- **Args werden als `JsonNode` ausgetauscht**, *nicht* als `String`. Das
  eliminiert die `Object args`-Polymorphie in `ImplementationPlan.ToolRequest`.
- **Idempotenz-IDs**: `ToolCall.id` wird vom Provider geliefert und in
  `ToolResult` zurückgegeben; ersetzt die heutigen `tool-1`, `writer-tool-1`
  Auto-IDs aus den Parsern.

## Risiken

| Risiko | Mitigation |
|---|---|
| Provider-Token-Kosten anders | Telemetrie + Benchmark vor/nach. |
| Modelle ohne Tool-Support brechen | `mode=LEGACY_JSON` bleibt Standard, AUTO nur Opt-In. |
| Prompt-Injection über Tool-Result-Inhalte | Tool-Results in dedizierte Content-Blocks, nicht in den User-Stream. |
| MCP-Tool-Schemas dynamisch | `McpToolCatalog.toToolDescriptors()` cached pro Bot. |

## Akzeptanzkriterien

- Mind. zwei Provider liefern `ChatTurn` mit echten `ToolCall`s in
  Integrationstest.
- Bot-Konfig-UI erlaubt das Umschalten von `agent.tooling.mode` pro Bot.
- Mit `mode=NATIVE_TOOLS` ist `AiResponseParser` für mind. den Coding-Pfad
  optional (Plan stammt aus `ChatTurn`, nicht aus String-Parsing).
- Bestehende Tests bleiben grün; neue Suite `NativeToolCallingIT`.

## Aufwand & Risiko

- Aufwand: ~6–8 Personentage (vier Provider).
- Risiko: hoch — größte Änderung in `AiClient`-Vertrag seit Bestehen des Bots.
- Voraussetzung: Schritt 3 (`AgentToolRouter`) und Schritt 5 (Schemas) komplett.

