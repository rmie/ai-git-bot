package org.remus.giteabot.agent.shared;

import org.remus.giteabot.agent.validation.ToolResult;

/**
 * Centralised description of {@link ToolResult} failures so that error messages
 * stay consistent across the coding and writer agents.
 */
public final class ToolFailures {

    private ToolFailures() {
    }

    /**
     * Returns a human-readable description of why a tool result represents a failure.
     * Falls back through {@code error}, {@code output}, and a generic message when
     * neither is populated.
     */
    public static String describe(ToolResult result) {
        if (result == null) {
            return "unknown tool failure";
        }
        String error = result.error();
        if (error != null && !error.isBlank()) {
            return error;
        }
        String output = result.output();
        if (output != null && !output.isBlank()) {
            return output;
        }
        return "tool returned no details";
    }
}

