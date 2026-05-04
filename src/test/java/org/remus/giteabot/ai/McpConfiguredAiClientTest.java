package org.remus.giteabot.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpConfiguredAiClientTest {

    @Test
    void chat_forwardsMcpConfigurationToDelegate() {
        CapturingAiClient delegate = new CapturingAiClient();
        McpConfigurationData mcpConfiguration = new McpConfigurationData("GitHub MCP",
                "{\"url\":\"https://api.githubcopilot.com/mcp/\"}");
        AiClient client = new McpConfiguredAiClient(delegate, mcpConfiguration);

        String result = client.chat(List.of(), "hello", "system", null, 100);

        assertEquals("ok", result);
        assertEquals(mcpConfiguration, delegate.capturedMcpConfiguration);
    }

    private static class CapturingAiClient implements AiClient {
        private McpConfigurationData capturedMcpConfiguration;

        @Override
        public String reviewDiff(String prTitle, String prBody, String diff) {
            return "ok";
        }

        @Override
        public String reviewDiff(String prTitle, String prBody, String diff, String systemPrompt, String modelOverride) {
            return "ok";
        }

        @Override
        public String chat(List<AiMessage> conversationHistory, String newUserMessage,
                           String systemPrompt, String modelOverride) {
            return "ok";
        }

        @Override
        public String chat(List<AiMessage> conversationHistory, String newUserMessage,
                           String systemPrompt, String modelOverride, Integer maxTokensOverride) {
            return "ok";
        }

        @Override
        public String chat(List<AiMessage> conversationHistory, String newUserMessage,
                           String systemPrompt, String modelOverride, Integer maxTokensOverride,
                           McpConfigurationData mcpConfiguration) {
            capturedMcpConfiguration = mcpConfiguration;
            return "ok";
        }
    }
}
