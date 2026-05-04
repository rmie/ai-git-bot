package org.remus.giteabot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Provider-agnostic interface for AI-powered code review.
 * Implementations exist for Anthropic, OpenAI, Ollama, and llama.cpp.
 */
public interface AiClient {

    Logger MCP_LOG = LoggerFactory.getLogger(AiClient.class);

    String reviewDiff(String prTitle, String prBody, String diff);

    String reviewDiff(String prTitle, String prBody, String diff, String systemPrompt, String modelOverride);

    /**
     * Reviews a pull request diff with additional context about the repository.
     *
     * @param additionalContext extra context (repo tree, file contents, commit messages, etc.)
     */
    default String reviewDiff(String prTitle, String prBody, String diff, String systemPrompt,
                              String modelOverride, String additionalContext) {
        return reviewDiff(prTitle, prBody, diff, systemPrompt, modelOverride);
    }

    /**
     * Sends a multi-turn conversation to the AI provider and returns the assistant's response.
     */
    String chat(List<AiMessage> conversationHistory, String newUserMessage,
                String systemPrompt, String modelOverride);

    /**
     * Sends a multi-turn conversation to the AI provider with a custom max tokens limit.
     *
     * @param maxTokensOverride Custom max tokens limit (if null, uses the default)
     */
    String chat(List<AiMessage> conversationHistory, String newUserMessage,
                String systemPrompt, String modelOverride, Integer maxTokensOverride);

    /**
     * Sends a multi-turn conversation with an optional MCP configuration.
     * Providers that do not support MCP should ignore it and continue normally.
     */
    default String chat(List<AiMessage> conversationHistory, String newUserMessage,
                        String systemPrompt, String modelOverride, Integer maxTokensOverride,
                        McpConfigurationData mcpConfiguration) {
        if (mcpConfiguration != null) {
            MCP_LOG.warn("AI provider {} does not support MCP configuration '{}'; continuing without MCP",
                    getClass().getSimpleName(), mcpConfiguration.name());
        }
        return chat(conversationHistory, newUserMessage, systemPrompt, modelOverride, maxTokensOverride);
    }

    /**
     * Reviews a pull request diff with an optional MCP configuration.
     * Providers that do not support MCP should ignore it and continue normally.
     */
    default String reviewDiff(String prTitle, String prBody, String diff, String systemPrompt,
                              String modelOverride, String additionalContext,
                              McpConfigurationData mcpConfiguration) {
        if (mcpConfiguration != null) {
            MCP_LOG.warn("AI provider {} does not support MCP configuration '{}'; continuing without MCP",
                    getClass().getSimpleName(), mcpConfiguration.name());
        }
        return reviewDiff(prTitle, prBody, diff, systemPrompt, modelOverride, additionalContext);
    }
}
