package org.remus.giteabot.ai;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class McpConfiguredAiClient implements AiClient {

    private final AiClient delegate;
    private final McpConfigurationData mcpConfiguration;

    public McpConfiguredAiClient(AiClient delegate, McpConfigurationData mcpConfiguration) {
        this.delegate = delegate;
        this.mcpConfiguration = mcpConfiguration;
    }

    @Override
    public String reviewDiff(String prTitle, String prBody, String diff) {
        return delegate.reviewDiff(prTitle, prBody, diff);
    }

    @Override
    public String reviewDiff(String prTitle, String prBody, String diff, String systemPrompt, String modelOverride) {
        return delegate.reviewDiff(prTitle, prBody, diff, systemPrompt, modelOverride);
    }

    @Override
    public String reviewDiff(String prTitle, String prBody, String diff, String systemPrompt,
                             String modelOverride, String additionalContext) {
        if (mcpConfiguration != null) {
            log.info("Applying MCP configuration '{}' to AI review request", mcpConfiguration.name());
        }
        return delegate.reviewDiff(prTitle, prBody, diff, systemPrompt, modelOverride,
                additionalContext, mcpConfiguration);
    }

    @Override
    public String chat(List<AiMessage> conversationHistory, String newUserMessage,
                       String systemPrompt, String modelOverride) {
        return chat(conversationHistory, newUserMessage, systemPrompt, modelOverride, null);
    }

    @Override
    public String chat(List<AiMessage> conversationHistory, String newUserMessage,
                       String systemPrompt, String modelOverride, Integer maxTokensOverride) {
        if (mcpConfiguration != null) {
            log.info("Applying MCP configuration '{}' to AI chat request", mcpConfiguration.name());
        }
        return delegate.chat(conversationHistory, newUserMessage, systemPrompt, modelOverride,
                maxTokensOverride, mcpConfiguration);
    }
}
