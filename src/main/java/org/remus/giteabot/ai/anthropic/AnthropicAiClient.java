package org.remus.giteabot.ai.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.ai.AbstractAiClient;
import org.remus.giteabot.ai.AiMessage;
import org.remus.giteabot.ai.McpConfigurationData;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
public class AnthropicAiClient extends AbstractAiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public AnthropicAiClient(RestClient restClient, String model, int maxTokens,
                             int maxDiffCharsPerChunk, int maxDiffChunks,
                             int retryTruncatedChunkChars) {
        super(model, maxTokens, maxDiffCharsPerChunk, maxDiffChunks, retryTruncatedChunkChars);
        this.restClient = restClient;
    }

    @Override
    protected String sendReviewRequest(String systemPrompt, String effectiveModel,
                                       int maxTokens, String userMessage) {
        return sendReviewRequest(systemPrompt, effectiveModel, maxTokens, userMessage, null);
    }

    @Override
    protected String sendReviewRequest(String systemPrompt, String effectiveModel,
                                       int maxTokens, String userMessage,
                                       McpConfigurationData mcpConfiguration) {
        AnthropicRequest request = AnthropicRequest.builder()
                .model(effectiveModel)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .messages(List.of(
                        AnthropicRequest.Message.builder()
                                .role("user")
                                .content(userMessage)
                                 .build()
                 ))
                .mcpServers(toAnthropicMcpServers(mcpConfiguration))
                .build();

        AnthropicResponse response = executeRequest(request, mcpConfiguration, "review");

        return extractText(response, "review");
    }

    @Override
    protected String sendChatRequest(String systemPrompt, String effectiveModel,
                                     int maxTokens, List<AiMessage> messages) {
        return sendChatRequest(systemPrompt, effectiveModel, maxTokens, messages, null);
    }

    @Override
    protected String sendChatRequest(String systemPrompt, String effectiveModel,
                                     int maxTokens, List<AiMessage> messages,
                                     McpConfigurationData mcpConfiguration) {
        List<AnthropicRequest.Message> anthropicMessages = messages.stream()
                .map(m -> AnthropicRequest.Message.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .build())
                .toList();

        AnthropicRequest request = AnthropicRequest.builder()
                .model(effectiveModel)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .messages(anthropicMessages)
                .mcpServers(toAnthropicMcpServers(mcpConfiguration))
                .build();

        AnthropicResponse response = executeRequest(request, mcpConfiguration, "chat");

        return extractText(response, "chat");
    }

    @Override
    protected boolean isPromptTooLongError(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body == null) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("prompt is too long") || normalized.contains("maximum");
    }

    private String extractText(AnthropicResponse response, String context) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            log.warn("Empty response from Anthropic API");
            return "Unable to generate " + context + " - empty response from AI.";
        }

        String result = response.getContent().stream()
                .filter(block -> "text".equals(block.getType()))
                .map(AnthropicResponse.ContentBlock::getText)
                .reduce("", (a, b) -> a + b);

        if (response.getUsage() != null) {
            log.info("Anthropic {} response: {} input tokens, {} output tokens",
                    context,
                    response.getUsage().getInputTokens(),
                    response.getUsage().getOutputTokens());
        }

        return result;
    }

    private AnthropicResponse executeRequest(AnthropicRequest request, McpConfigurationData mcpConfiguration,
                                             String context) {
        try {
            return restClient.post()
                    .uri("/v1/messages")
                    .body(request)
                    .retrieve()
                    .body(AnthropicResponse.class);
        } catch (RestClientException e) {
            if (mcpConfiguration == null) {
                throw e;
            }
            log.error("MCP configuration '{}' could not be applied to Anthropic {} request; retrying without MCP: {}",
                    mcpConfiguration.name(), context, e.getMessage(), e);
            request.setMcpServers(null);
            return restClient.post()
                    .uri("/v1/messages")
                    .body(request)
                    .retrieve()
                    .body(AnthropicResponse.class);
        }
    }

    private List<JsonNode> toAnthropicMcpServers(McpConfigurationData mcpConfiguration) {
        if (mcpConfiguration == null || mcpConfiguration.json() == null || mcpConfiguration.json().isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(mcpConfiguration.json());
            List<JsonNode> servers = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(servers::add);
            } else if (root.has("mcp_servers") && root.get("mcp_servers").isArray()) {
                root.get("mcp_servers").forEach(servers::add);
            } else if (root.has("mcpServers") && root.get("mcpServers").isArray()) {
                root.get("mcpServers").forEach(servers::add);
            } else {
                servers.add(root);
            }
            return servers;
        } catch (Exception e) {
            log.error("MCP configuration '{}' is not valid for Anthropic requests; continuing without MCP: {}",
                    mcpConfiguration.name(), e.getMessage(), e);
            return null;
        }
    }
}
