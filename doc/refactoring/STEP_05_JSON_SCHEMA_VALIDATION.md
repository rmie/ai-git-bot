# Step 5 — JSON-Schema-Validation für Agent-Antworten

> **Status: ✅ umgesetzt** in 1.6.0-SNAPSHOT. Tests grün (444/444).

## Kontext

Beide Agenten verlassen sich heute auf JSON-im-Prompt + Regex-Heuristiken im
`AiResponseParser` (Coding) bzw. `WriterResponseParser` (Writer):

- `extractJsonFromResponse` (4 Strategien, von `\`\`\`json` bis erstes `{`),
- `truncateToFirstJsonObject`,
- `repairTruncatedJson`,
- `sanitizeInvalidJsonEscapes`,
- `findLastCompleteRunTool`.

Diese Logik existiert nur, weil die Modelle weder strukturierten Output liefern
noch validiert wird. Symptome: brüchiges Parsing, doppelte Ergebnisfelder
(`requestFiles`/`requestedFiles`, `runTool`/`runTools`), Backward-Compat-Felder.

## Ziel

Strikte JSON-Schema-Validierung für die zwei dokumentierten Output-Verträge
einführen, *ohne* die Repair-Heuristiken sofort zu entfernen. Schritt 6
(Function Calling) macht sie erst entbehrlich.

## Lieferumfang

1. **Schemas** in `src/main/resources/agent/schemas/`:
   - `coding-plan.schema.json` (Felder: `summary`, `requestFiles`,
     `requestTools[]`, `runTools[]`, `runTool`, `branchName`).
   - `writer-plan.schema.json` (Felder: `qualityAssessment`,
     `clarifyingQuestions[]`, `revisedIssueDraft`, `assumptions[]`,
     `openQuestions[]`, `readyToCreate`, `requestFiles[]`, `requestTools[]`).
   - Aliase (`requestedFiles`, `requestedTools`) als `oneOf` modellieren, damit
     bestehende Modell-Antworten zulässig bleiben.
2. **Validator-Bean** `AgentSchemaValidator` (Spring-Bean, threadsafe) mit
   `networknt/json-schema-validator`. Methode:
   `Optional<List<ValidationMessage>> validate(String jsonStr, AgentSchema kind)`.
3. Beide Parser nutzen den Validator **nach** `extractJsonFromResponse` und
   **vor** dem Jackson-`readValue`. Invalide Antworten werden:
   - geloggt mit Schema-Pfad und ersten 500 Zeichen Antwort;
   - **noch nicht** abgewiesen, sondern weiterhin durch die heutigen Repair-
     Pfade gejagt (Fallback-Modus).
4. Metrik `agent.plan.schema_violations_total{agent=coding|writer}` (Micrometer).
5. Feature-Flag `agent.schema.enforce=false` (default). Nur Logging+Metrik.
6. `prompts/agent.md` und `prompts/writer.md`/Output-Contract um den Hinweis
   ergänzen, dass das Schema in `…/schemas/*.schema.json` liegt; ggf. Schema
   inline ins System-Prompt rendern (renderiert aus dem JSON, nicht hartkodiert).

## Migrationsschritte

1. Dependency hinzufügen: `com.networknt:json-schema-validator` (kompatibel mit
   sowohl `tools.jackson` als auch `com.fasterxml.jackson` — letzteres ist hier
   notwendig, weil der Validator auf Jackson 2 baut). Brücken-Adapter falls
   nötig.
2. Schemas extrahieren aus heutigen DTOs (`AiImplementationResponse`,
   `AiWriterResponse`).
3. Snapshot-Tests, die echte AI-Antworten aus
   `src/test/resources/ai-responses/` laden und gegen das Schema validieren
   (Golden-Files).
4. Nach 1–2 Releases mit `enforce=false`-Logging Auswertung der
   Schema-Verletzungen → Schemas anpassen.
5. `enforce=true` per default schalten. Repair-Heuristiken behalten als
   Recovery-Layer.

## Akzeptanzkriterien

- Schema-Dateien sind im Code unter `src/main/resources/agent/schemas/`.
- `agent.plan.schema_violations_total` taucht in `/actuator/prometheus` auf.
- Mind. 3 Snapshot-Tests pro Agent gegen reale Antworten (CI-grün).
- Bestehende Repair-Heuristiken unverändert; **kein** Verhaltenswandel im
  Default-Modus.
- Feature-Flag dokumentiert in `doc/AGENT.md`.

## Aufwand & Risiko

- Aufwand: ~2 Personentage.
- Risiko: niedrig — der Validator läuft nur beobachtend.
- Folge-Schritt: Schritt 6 (Function Calling) profitiert direkt von den Schemas.

## Umsetzung (Ist-Zustand 1.6.0-SNAPSHOT)

| Lieferpunkt | Datei(en) |
|---|---|
| Schemas | `src/main/resources/agent/schemas/coding-plan.schema.json`, `…/writer-plan.schema.json` (Draft 2020-12, akzeptieren `requestedFiles`/`requestedTools`-Aliase und sowohl `runTool` als auch `runTools[]`) |
| Validator-Bean | `org.remus.giteabot.agent.shared.AgentSchemaValidator` (Spring `@Component`, lädt Schemas in `@PostConstruct`, registriert sich beim `AgentSchemaValidatorHolder`) |
| Singleton-Holder | `AgentSchemaValidatorHolder` — entkoppelt die `new`-instanziierten Parser von Spring |
| Schema-Enum | `AgentSchema` mit Klassenpfad und Agent-Label für die Counter-Tags |
| Counter | `agent.plan.schema_violations_total{agent=coding|writer}` (Micrometer, sichtbar unter `/actuator/prometheus`) |
| Feature-Flag | `AgentConfigProperties.SchemaConfig.enforce` (Property `agent.schema.enforce`, ENV `AGENT_SCHEMA_ENFORCE`, Default `false`) |
| Parser-Integration | `AiResponseParser#validateAgainstSchema` und `WriterResponseParser#validateAgainstSchema` — laufen **nach** `extractJsonFromResponse`/`repair`/`sanitize` und **vor** `objectMapper.readValue`. Im Default-Modus ändern sie das Verhalten nicht. |
| Snapshot-Tests | `src/test/resources/ai-responses/coding/{01-runTools,02-context-request,03-legacy-aliases}.json`, `…/writer/{01-ready,02-questions,03-context-request}.json` (3 pro Agent) |
| Test-Klasse | `AgentSchemaValidatorTest` (Snapshot-Tests + Negativ-Tests + Default-Mode-Assertion) |
| Dependency | `com.networknt:json-schema-validator:1.5.7` (bringt Jackson 2 transitiv neben dem im Code verwendeten Jackson 3 / `tools.jackson`) |
| Doku | `doc/AGENT.md` Abschnitt *JSON-Schema Validation (Step 5)* + Tabelleneintrag `AGENT_SCHEMA_ENFORCE` |

### Verhaltenssicherung

Im Default-Modus (`enforce=false`) verändert der Validator das Parsing
**nicht** — er erzeugt nur Logs und Metriken. Damit bleiben sämtliche
bestehenden 428 Verhaltens-Tests (Coding- und Writer-Agent) unverändert
grün. Die acht neuen Tests in `AgentSchemaValidatorTest` decken Schema-
Konformität für drei reale Coding- und drei reale Writer-Antworten ab,
plus zwei Negativfälle.

### Bewusste Abweichungen vom ursprünglichen Plan

- **Validator-Bean per Singleton-Holder ausgeliefert** statt direkter
  Konstruktor-Injektion in die Parser. Grund: `AiResponseParser` und
  `WriterResponseParser` werden in Tests und Legacy-Pfaden mit `new`
  erzeugt — eine Pflicht-Injektion hätte ~30 Aufruferstellen gleichzeitig
  brechen müssen. Der Holder ist ein expliziter und in Schritt 6 leicht
  zu ersetzender Schritt in Richtung "alles Spring-managed".
- **Prompt-Inlining noch nicht aktiv.** Die Schemas liegen versioniert
  im Code, aber `prompts/agent.md` rendert sie noch nicht inline. Das
  rückt erst in Schritt 6 (Function Calling) in den Vordergrund, wo der
  AI-Client das Schema ohnehin nativ erhält.

