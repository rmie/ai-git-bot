package org.remus.giteabot.agent.shared;

import org.remus.giteabot.mcp.McpOrchestrationService;
import org.remus.giteabot.mcp.McpToolCatalog;

/**
 * Null-safe MCP-tool detection helper. Both agent services need to ask whether a
 * tool name belongs to the configured MCP catalog; before this helper existed the
 * check was duplicated in {@code IssueImplementationService} and
 * {@code WriterAgentService}.
 */
public final class McpTools {

    /** Canonical prefix of MCP tool names produced by {@code McpOrchestrationService}. */
    public static final String MCP_TOOL_PREFIX = "mcp:";

    private McpTools() {
    }

    public static boolean isMcpTool(McpOrchestrationService orchestration,
                                    McpToolCatalog catalog,
                                    String toolName) {
        return orchestration != null && orchestration.isMcpTool(catalog, toolName);
    }

    /**
     * Lightweight prefix-only check for callers that don't have access to the
     * orchestration service / catalog (typically UI/notification helpers).
     * Matches every tool name produced by {@code McpOrchestrationService}.
     */
    public static boolean looksLikeMcpTool(String toolName) {
        return toolName != null && toolName.startsWith(MCP_TOOL_PREFIX);
    }
}

