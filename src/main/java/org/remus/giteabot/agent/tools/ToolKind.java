package org.remus.giteabot.agent.tools;

/**
 * Classification of a tool the AI agent can invoke. The router uses the kind to
 * decide which underlying executor to call and which tools should be advertised
 * for which agent role.
 */
public enum ToolKind {

    /**
     * Read-only repository exploration (rg, cat, find, tree, git-log, …) plus the
     * special {@code branch-switcher} workspace operation. Results are silent —
     * never posted as public comments.
     */
    CONTEXT,

    /**
     * Workspace-mutating file tools (write-file, patch-file, mkdir, delete-file).
     * Silent.
     */
    FILE,

    /**
     * External validation tools (mvn, npm, gradle, …) configured via
     * {@code AgentConfigProperties.validation.available-tools}. Results ARE
     * posted as comments.
     */
    VALIDATION,

    /**
     * A tool exposed by an MCP server.
     */
    MCP,

    /**
     * Repository API helpers used by the writer agent (get-issue, search-issues).
     * Silent.
     */
    REPOSITORY,

    /**
     * Tool name is not configured for the current agent role.
     */
    UNKNOWN
}

