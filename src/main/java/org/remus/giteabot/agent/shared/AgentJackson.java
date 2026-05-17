package org.remus.giteabot.agent.shared;

import tools.jackson.databind.ObjectMapper;

/**
 * Singleton holder for a shared Jackson {@link ObjectMapper} used by all
 * agent components.
 * <p>
 * {@code ObjectMapper} is documented as thread-safe after configuration, so a
 * single instance can be reused across the whole agent module. Keeping a
 * shared instance avoids the per-service allocation that previously existed
 * in {@code AiResponseParser}, {@code WriterResponseParser} and
 * {@code WriterAgentService}.
 */
public final class AgentJackson {

    private static final ObjectMapper INSTANCE = new ObjectMapper();

    private AgentJackson() {
    }

    public static ObjectMapper mapper() {
        return INSTANCE;
    }
}

