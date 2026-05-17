package org.remus.giteabot.agent.shared;

/**
 * Identifies a JSON-Schema bundled under
 * {@code src/main/resources/agent/schemas/} that constrains the structured
 * output of one of the agents.
 *
 * <p>Step 5 of the agent refactor introduces schema-based validation alongside
 * the existing repair heuristics. The validator runs in observe-only mode by
 * default and is graduated to enforce mode via the {@code agent.schema.enforce}
 * feature flag once we have collected enough field telemetry.</p>
 */
public enum AgentSchema {

    CODING_PLAN("agent/schemas/coding-plan.schema.json", "coding"),
    WRITER_PLAN("agent/schemas/writer-plan.schema.json", "writer");

    private final String classpathLocation;
    private final String agentLabel;

    AgentSchema(String classpathLocation, String agentLabel) {
        this.classpathLocation = classpathLocation;
        this.agentLabel = agentLabel;
    }

    public String classpathLocation() {
        return classpathLocation;
    }

    /**
     * Stable label used as the {@code agent} tag for the
     * {@code agent.plan.schema_violations_total} Micrometer counter.
     */
    public String agentLabel() {
        return agentLabel;
    }
}

