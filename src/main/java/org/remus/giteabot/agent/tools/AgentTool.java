package org.remus.giteabot.agent.tools;

import org.remus.giteabot.agent.validation.ToolResult;

/**
 * A tool the AI agent can request. Implementations are stateless and resolved
 * by name through {@link AgentToolRouter}.
 */
public interface AgentTool {

    /** Canonical lower-case name as the AI must spell it in its plan. */
    String name();

    /** Classification used for dispatch and prompt rendering. */
    ToolKind kind();

    /**
     * Whether the tool's output should be hidden from public issue/PR comments.
     * All current context, file and repository tools are silent; validation tools
     * are not.
     */
    default boolean silent() {
        return kind() != ToolKind.VALIDATION;
    }

    /** Executes the request and returns its raw result. */
    ToolResult execute(ToolCallContext context);
}

