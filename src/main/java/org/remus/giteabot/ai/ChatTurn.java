package org.remus.giteabot.ai;

import java.util.List;

/**
 * Result of a single round-trip to the AI provider when using native tool
 * calling (Step 6). {@code assistantText} may be empty when the model only
 * emits tool calls.
 */
public record ChatTurn(String assistantText,
                       List<ToolCall> toolCalls,
                       StopReason stopReason) {

    public ChatTurn {
        if (toolCalls == null) {
            toolCalls = List.of();
        }
        if (stopReason == null) {
            stopReason = StopReason.OTHER;
        }
        if (assistantText == null) {
            assistantText = "";
        }
    }

    /** Convenience factory for a pure-text (no tool-use) turn. */
    public static ChatTurn text(String assistantText) {
        return new ChatTurn(assistantText, List.of(), StopReason.END_TURN);
    }

    /** True when the model wants the caller to dispatch one or more tools. */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
