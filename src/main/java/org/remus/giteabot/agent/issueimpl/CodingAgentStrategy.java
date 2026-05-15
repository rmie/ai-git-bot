package org.remus.giteabot.agent.issueimpl;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.agent.loop.AgentRunContext;
import org.remus.giteabot.agent.loop.AgentStrategy;
import org.remus.giteabot.agent.loop.LoopOutcome;
import org.remus.giteabot.agent.loop.StepDecision;
import org.remus.giteabot.agent.model.ImplementationPlan;
import org.remus.giteabot.agent.session.AgentSessionService;
import org.remus.giteabot.agent.shared.BranchSwitcher;
import org.remus.giteabot.agent.shared.McpTools;
import org.remus.giteabot.agent.tools.AgentToolRouter;
import org.remus.giteabot.agent.tools.ToolCallContext;
import org.remus.giteabot.agent.validation.ToolExecutionService;
import org.remus.giteabot.agent.validation.ToolResult;
import org.remus.giteabot.agent.validation.WorkspaceService;
import org.remus.giteabot.config.AgentConfigProperties;
import org.remus.giteabot.mcp.McpOrchestrationService;
import org.remus.giteabot.mcp.McpToolCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * {@link AgentStrategy} for the coding agent. Encapsulates the previously
 * inline {@code runToolImplementationLoop} logic, including:
 * <ul>
 *     <li>Separate sub-budgets for context-fetch rounds vs. tool-execution rounds
 *         (replaces the legacy {@code attempt--} hack).</li>
 *     <li>Validation-policy handling
 *         (including {@code IGNORE_MCP_AFTER_VALIDATION_SUCCESS}).</li>
 *     <li>Workspace-change detection with retry feedback.</li>
 *     <li>Branch-switcher integration during context fetches.</li>
 * </ul>
 *
 * <p>Strategies hold per-run mutable state (counters, current branch) and
 * therefore must be instantiated fresh for every loop run.</p>
 */
@Slf4j
public final class CodingAgentStrategy implements AgentStrategy {

    private final String systemPrompt;
    private final AgentPromptBuilder promptBuilder;
    private final AiResponseParser responseParser;
    private final IssueNotificationService notificationService;
    private final AgentSessionService sessionService;
    private final BranchSwitcher branchSwitcher;
    private final AgentToolRouter toolRouter;
    private final ToolExecutionService toolExecutionService;
    private final WorkspaceService workspaceService;
    private final AgentConfigProperties agentConfig;
    private final McpOrchestrationService mcpOrchestrationService;
    private final McpToolCatalog mcpToolCatalog;
    private final ContextFetcher fetchContext;

    private final int maxToolRounds;
    private final int maxRetries;
    private final int maxContextRounds;
    private int fileRequestRounds = 0;
    private int toolRounds = 0;
    private int attempt = 1;
    private ImplementationPlan lastSuccessfulPlan;

    /** Functional hook so the strategy stays decoupled from the surrounding service's helpers. */
    @FunctionalInterface
    public interface ContextFetcher {
        String fetch(String owner, String repo, String branch,
                     List<String> files, List<ImplementationPlan.ToolRequest> tools,
                     java.nio.file.Path workspaceDir);
    }

    public CodingAgentStrategy(String systemPrompt,
                               AgentPromptBuilder promptBuilder,
                               AiResponseParser responseParser,
                               IssueNotificationService notificationService,
                               AgentSessionService sessionService,
                               BranchSwitcher branchSwitcher,
                               AgentToolRouter toolRouter,
                               ToolExecutionService toolExecutionService,
                               WorkspaceService workspaceService,
                               AgentConfigProperties agentConfig,
                               McpOrchestrationService mcpOrchestrationService,
                               McpToolCatalog mcpToolCatalog,
                               ContextFetcher contextFetcher) {
        this.systemPrompt = systemPrompt;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.notificationService = notificationService;
        this.sessionService = sessionService;
        this.branchSwitcher = branchSwitcher;
        this.toolRouter = toolRouter;
        this.toolExecutionService = toolExecutionService;
        this.workspaceService = workspaceService;
        this.agentConfig = agentConfig;
        this.mcpOrchestrationService = mcpOrchestrationService;
        this.mcpToolCatalog = mcpToolCatalog;
        this.fetchContext = contextFetcher;
        this.maxRetries = agentConfig.getValidation().isEnabled()
                ? agentConfig.getValidation().getMaxRetries() : 1;
        this.maxToolRounds = agentConfig.getValidation().getMaxToolExecutions();
        this.maxContextRounds = agentConfig.getBudget().getMaxContextRounds();
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }


    @Override
    public StepDecision step(AgentRunContext ctx, String aiResponse, int round) {
        // Sub-budget exhausted? -> fail.
        if (attempt > maxRetries) {
            log.warn("Tool implementation loop exhausted {} attempts without full success", maxRetries);
            return new StepDecision.Finish(LoopOutcome.fail(ctx.baseBranch()));
        }
        log.info("Tool implementation loop for issue #{}, attempt {}/{}",
                ctx.issueNumber(), attempt, maxRetries);

        notificationService.postAiThinkingComment(ctx.owner(), ctx.repo(), ctx.issueNumber(), aiResponse);

        ImplementationPlan plan = responseParser.parseAiResponse(aiResponse);
        if (plan == null) {
            log.warn("Failed to parse AI response on attempt {}", attempt);
            return new StepDecision.Finish(LoopOutcome.fail(ctx.baseBranch()));
        }
        // Step 7.1 — persist the latest parsed plan on the session so PR-body
        // and follow-up comment generation no longer need to re-parse the
        // entire conversation history.
        sessionService.recordPlan(ctx.session(), plan.getSummary(), aiResponse);

        // 1) Context-only request (no tool requests yet): fetch and continue without spending an attempt.
        if (plan.hasContextRequests() && !plan.hasToolRequest() && fileRequestRounds < maxContextRounds) {
            fileRequestRounds++;
            log.info("AI requesting additional context (round {}/{})", fileRequestRounds, maxContextRounds);
            BranchSwitcher.Result branchSwitchResult = branchSwitcher.apply(
                    ctx.workspaceDir(), ctx.baseBranch(), plan.getRequestTools(), ctx.issueNumber());
            ctx.setBaseBranch(branchSwitchResult.selectedBranch());
            String fetched = fetchContext.fetch(ctx.owner(), ctx.repo(), ctx.baseBranch(),
                    plan.getRequestFiles(), branchSwitchResult.remainingToolRequests(), ctx.workspaceDir());
            String ctxMsg = "Here is the requested repository context:\n" + fetched
                    + "\n\nNow implement the issue using `runTools`. "
                    + "Use write-file/patch-file for changes and include validation tools.";
            return new StepDecision.Continue(ctxMsg);
        }

        // 2) No tool requests at all -> ask AI to provide them. Counts as an attempt (legacy behaviour).
        if (!plan.hasToolRequest()) {
            log.info("AI provided no runTools on attempt {}", attempt);
            attempt++;
            return new StepDecision.Continue(promptBuilder.buildMissingToolFeedback());
        }

        // 3) Guard against runaway tool rounds.
        if (toolRounds >= maxToolRounds) {
            log.warn("Reached max tool rounds ({}) — returning current result", maxToolRounds);
            return new StepDecision.Finish(LoopOutcome.fail(ctx.baseBranch()));
        }
        toolRounds++;

        // 4) Execute the requested tools.
        List<ImplementationPlan.ToolRequest> requests = plan.getEffectiveToolRequests();
        List<ToolResult> results = executeAllTools(ctx.workspaceDir(), requests);
        boolean hasValidationTools = hasValidationTools(requests);
        boolean validationPassed = !hasValidationTools || allValidationToolsPassed(requests, results);

        // 5) Surface non-silent tool results as comments.
        for (int i = 0; i < requests.size(); i++) {
            ImplementationPlan.ToolRequest req = requests.get(i);
            if (!toolExecutionService.isSilentTool(req.getTool()) && !isMcpTool(req.getTool())) {
                notificationService.postToolResultComment(ctx.owner(), ctx.repo(), ctx.issueNumber(),
                        req, results.get(i));
            }
        }

        // 6) Blocking non-validation failures -> retry with feedback.
        if (hasBlockingNonValidationToolFailures(requests, results, validationPassed)) {
            log.info("One or more non-validation tools failed on attempt {}; asking AI to correct", attempt);
            attempt++;
            return new StepDecision.Continue(promptBuilder.buildMultiToolFeedback(requests, results));
        }

        // 7) Validation disabled or no validation tools -> success-after-changes.
        if (!agentConfig.getValidation().isEnabled() || !hasValidationTools) {
            return finalizeOrRetry(ctx, plan, requests, results);
        }

        // 8) Validation passed -> success-after-changes.
        if (validationPassed) {
            log.info("All validation tools passed on attempt {}", attempt);
            return finalizeOrRetry(ctx, plan, requests, results);
        }

        // 9) Validation failed -> feedback, retry.
        attempt++;
        return new StepDecision.Continue(promptBuilder.buildMultiToolFeedback(requests, results));
    }

    @Override
    public LoopOutcome onBudgetExhausted(AgentRunContext ctx) {
        log.warn("AgentLoop budget exhausted for issue #{}", ctx.issueNumber());
        return LoopOutcome.fail(ctx.baseBranch());
    }

    private StepDecision finalizeOrRetry(AgentRunContext ctx,
                                         ImplementationPlan plan,
                                         List<ImplementationPlan.ToolRequest> requests,
                                         List<ToolResult> results) {
        if (workspaceService.hasUncommittedChanges(ctx.workspaceDir())) {
            lastSuccessfulPlan = plan;
            return new StepDecision.Finish(LoopOutcome.success(ctx.baseBranch(), plan));
        }
        log.info("Tool execution produced no Git-detectable workspace changes; asking AI to correct");
        attempt++;
        return new StepDecision.Continue(buildNoWorkspaceChangesFeedback(requests, results));
    }

    private String buildNoWorkspaceChangesFeedback(List<ImplementationPlan.ToolRequest> requests,
                                                   List<ToolResult> results) {
        return promptBuilder.buildMultiToolFeedback(requests, results)
                + "\n\nNo file changes are currently present in the git workspace. "
                + "Your previous file tools either failed, made no effective change, or only created empty directories. "
                + "Inspect the files with context tools if needed, then use write-file or patch-file so Git has actual changes to commit.";
    }

    private List<ToolResult> executeAllTools(java.nio.file.Path workspaceDir,
                                             List<ImplementationPlan.ToolRequest> requests) {
        List<ToolResult> results = new ArrayList<>();
        for (ImplementationPlan.ToolRequest req : requests) {
            results.add(toolRouter.execute(AgentToolRouter.Mode.CODING,
                    new ToolCallContext(null, null, null, workspaceDir, req)));
        }
        return results;
    }

    private boolean hasValidationTools(List<ImplementationPlan.ToolRequest> requests) {
        return requests.stream().anyMatch(r -> toolExecutionService.isValidationTool(r.getTool()));
    }

    private boolean allValidationToolsPassed(List<ImplementationPlan.ToolRequest> requests,
                                             List<ToolResult> results) {
        return IntStream.range(0, requests.size())
                .filter(i -> toolExecutionService.isValidationTool(requests.get(i).getTool()))
                .allMatch(i -> results.get(i).success());
    }

    private boolean hasNonValidationToolFailures(List<ImplementationPlan.ToolRequest> requests,
                                                 List<ToolResult> results) {
        return IntStream.range(0, requests.size())
                .filter(i -> !toolExecutionService.isValidationTool(requests.get(i).getTool()))
                .anyMatch(i -> !results.get(i).success());
    }

    private boolean hasBlockingNonValidationToolFailures(List<ImplementationPlan.ToolRequest> requests,
                                                         List<ToolResult> results,
                                                         boolean validationPassed) {
        if (!hasNonValidationToolFailures(requests, results)) {
            return false;
        }
        AgentConfigProperties.ValidationConfig.NonValidationFailurePolicy policy =
                agentConfig.getValidation().getNonValidationFailurePolicy();
        if (policy == AgentConfigProperties.ValidationConfig.NonValidationFailurePolicy.IGNORE_MCP_AFTER_VALIDATION_SUCCESS
                && validationPassed
                && hasOnlyMcpNonValidationFailures(requests, results)) {
            log.info("Ignoring MCP non-validation tool failures because validation passed");
            return false;
        }
        return true;
    }

    private boolean hasOnlyMcpNonValidationFailures(List<ImplementationPlan.ToolRequest> requests,
                                                    List<ToolResult> results) {
        return IntStream.range(0, requests.size())
                .filter(i -> !toolExecutionService.isValidationTool(requests.get(i).getTool()))
                .filter(i -> !results.get(i).success())
                .allMatch(i -> isMcpTool(requests.get(i).getTool()));
    }

    private boolean isMcpTool(String toolName) {
        return McpTools.isMcpTool(mcpOrchestrationService, mcpToolCatalog, toolName);
    }
}




