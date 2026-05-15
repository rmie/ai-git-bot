package org.remus.giteabot.ai;

import java.util.List;

/**
 * Shared helper for {@link AiClient} implementations that need to fall back
 * to the legacy textual {@code chat()} API when native tool calling is
 * disabled or when the caller did not supply any tool descriptors.
 *
 * <p>Lives in the {@code ai} package so all provider implementations
 * (anthropic, openai, …) can use the same delegation rather than each
 * carrying its own copy.</p>
 */
public final class AiClientDelegateSupport {

    private AiClientDelegateSupport() {
    }

    public static ChatTurn delegateToChat(AiClient client,
                                          List<AiMessage> conversationHistory,
                                          String newUserMessage,
                                          String systemPrompt,
                                          String modelOverride,
                                          Integer maxTokensOverride) {
        String text = client.chat(conversationHistory,
                newUserMessage == null ? "" : newUserMessage,
                systemPrompt, modelOverride, maxTokensOverride);
        return ChatTurn.text(text);
    }
}

