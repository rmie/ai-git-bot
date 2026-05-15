package org.remus.giteabot.ai;

import tools.jackson.databind.JsonNode;

/**
 * A single native tool/function invocation produced by an AI provider.
 *
 * <p>{@code id} is provider-generated and must be echoed back when sending
 * the tool's result, so the model can correlate request and response.
 * {@code args} is the raw JSON object the model produced for the call (not
 * a stringified version).</p>
 */
public record ToolCall(String id, String name, JsonNode args) {
}
