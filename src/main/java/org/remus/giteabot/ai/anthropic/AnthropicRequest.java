package org.remus.giteabot.ai.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnthropicRequest {

    private String model;

    @JsonProperty("max_tokens")
    private int maxTokens;

    private String system;

    private List<Message> messages;

    @JsonProperty("mcp_servers")
    private List<JsonNode> mcpServers;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
