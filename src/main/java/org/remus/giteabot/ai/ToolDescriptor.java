package org.remus.giteabot.ai;

import tools.jackson.databind.JsonNode;

/**
 * Describes a tool the AI provider should expose to the model when using
 * native function/tool calling (Step 6).
 *
 * <p>{@code jsonSchema} must be a Draft-2020-12 JSON-Schema object (subset
 * accepted by each provider — Anthropic, OpenAI and Google all accept a
 * common subset).</p>
 */
public record ToolDescriptor(String name, String description, JsonNode jsonSchema) {
}
