package org.remus.giteabot.ai;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * A single native tool/function invocation produced by an AI provider.
 *
 * <p>{@code id} is provider-generated and must be echoed back when sending
 * the tool's result, so the model can correlate request and response.
 * {@code args} is the raw JSON object the model produced for the call (not
 * a stringified version).</p>
 *
 * <p>{@code providerMetadata} carries opaque provider-specific extras that
 * must round-trip through the conversation history. Currently used for
 * Gemini 3.x {@code thoughtSignature} values, which the API requires to be
 * replayed verbatim on every {@code functionCall} part — but the abstraction
 * is intentionally a free-form string map so other providers can stash
 * similar metadata (signed tool-call attestations, vendor request ids, ...)
 * without further changes to this record. May be {@code null} when the
 * provider has no extras to preserve.</p>
 */
public record ToolCall(String id, String name, JsonNode args, Map<String, String> providerMetadata) {

    /** Backwards-compatible constructor for providers that do not surface any extras. */
    public ToolCall(String id, String name, JsonNode args) {
        this(id, name, args, null);
    }
}
