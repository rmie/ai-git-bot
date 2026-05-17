package org.remus.giteabot.ai;

/**
 * Provider-agnostic helper for sanitising tool / function names that are
 * sent to LLM APIs.
 *
 * <p>Most provider tool-calling APIs validate function names against a
 * restricted alphabet (Anthropic and OpenAI accept {@code [a-zA-Z0-9_-]},
 * Google Gemini explicitly rejects names with more than one colon). The
 * project, however, exposes MCP tools system-wide as
 * {@code mcp:<server>:<tool>}, which fails those validators.</p>
 *
 * <p>To keep behaviour consistent across providers and avoid surprise 400s
 * whenever a new MCP server is wired up, every native tool-calling client
 * funnels tool names through this helper:</p>
 * <ul>
 *   <li>{@link #sanitize(String)} is applied when <em>sending</em> a name
 *       to the provider — both for the tool declaration and when replaying
 *       prior assistant {@code tool_use}/{@code tool_call}/{@code functionCall}
 *       parts and their matching tool results.</li>
 *   <li>{@link #desanitize(String)} is applied when <em>reading</em> a name
 *       back from the provider, so the rest of the system continues to see
 *       the canonical MCP-style name (e.g. {@code mcp:github:issue_read}).</li>
 * </ul>
 *
 * <p>The mapping is a reversible substitution of {@code ":"} with
 * {@value #COLON_PLACEHOLDER}. The placeholder is chosen so that it stays
 * inside the {@code [a-zA-Z0-9_-]} character class accepted by every
 * provider and is highly unlikely to collide with characters used in real
 * tool names.</p>
 */
public final class ToolNameSanitizer {

    /** Placeholder substituted for {@code ':'} in provider-bound tool names. */
    public static final String COLON_PLACEHOLDER = "__";

    private ToolNameSanitizer() {
        // utility
    }

    /**
     * Convert a canonical tool name into a provider-safe form.
     * Returns {@code null} when the input is {@code null}.
     */
    public static String sanitize(String name) {
        return name == null ? null : name.replace(":", COLON_PLACEHOLDER);
    }

    /**
     * Reverse a previously {@link #sanitize(String) sanitised} name.
     * Returns {@code null} when the input is {@code null}.
     */
    public static String desanitize(String name) {
        return name == null ? null : name.replace(COLON_PLACEHOLDER, ":");
    }
}

