package org.remus.giteabot.agent.shared;

/**
 * Process-wide singleton holder for {@link AgentSchemaValidator}.
 *
 * <p>The agent parsers ({@code AiResponseParser}, {@code WriterResponseParser})
 * are instantiated with {@code new} in several legacy code paths and unit
 * tests, so they cannot rely on Spring constructor injection. Spring sets the
 * holder once during application startup; non-Spring callers degrade
 * gracefully (no validation is performed).</p>
 */
public final class AgentSchemaValidatorHolder {

    private static volatile AgentSchemaValidator instance;

    private AgentSchemaValidatorHolder() {
    }

    static void set(AgentSchemaValidator validator) {
        instance = validator;
    }

    public static AgentSchemaValidator get() {
        return instance;
    }
}

