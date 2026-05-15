package org.remus.giteabot.ai;

/**
 * Provider-agnostic reason why a {@link ChatTurn} ended.
 */
public enum StopReason {
    /** Model produced a complete final assistant message. */
    END_TURN,
    /** Model wants the caller to execute tool calls and continue the turn. */
    TOOL_USE,
    /** Provider truncated the response because the token budget was hit. */
    MAX_TOKENS,
    /** Anything else (content filter, provider error mapped to ChatTurn, …). */
    OTHER
}
