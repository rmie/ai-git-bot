package org.remus.giteabot.agent.loop;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.agent.session.AgentSessionService;
import org.remus.giteabot.agent.shared.AgentMetricsHolder;
import org.remus.giteabot.ai.AiClient;
import org.remus.giteabot.ai.AiMessage;
import org.remus.giteabot.ai.ChatTurn;
import org.remus.giteabot.ai.ToolDescriptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generic agent loop that owns the chat/history/session-sync mechanics shared
 * by every agent flavour. The agent-specific decision logic lives in an
 * {@link AgentStrategy}.
 *
 * <p>Step 6: every round publishes the
 * {@code agent.tool_call.mode_total{mode, provider}} counter and the
 * {@code agent.tool_call.latency_seconds{mode, provider}} timer via
 * {@link AgentMetricsHolder}. When the strategy requests
 * {@link ToolingMode#NATIVE} <em>and</em> the resolved
 * {@link AiClient#supportsNativeTools()} returns {@code true} <em>and</em>
 * the strategy supplies non-empty {@link AgentStrategy#toolDescriptors()},
 * the loop calls {@link AiClient#chatWithTools chatWithTools} and forwards
 * the structured {@link ChatTurn} to
 * {@link AgentStrategy#step(AgentRunContext, ChatTurn, int)}. Otherwise the
 * loop transparently falls back to the legacy text path.</p>
 */
@Slf4j
public final class AgentLoop {

    private final AiClient aiClient;
    private final AgentSessionService sessionService;
    private final AgentBudget budget;
    private final String providerTag;

    public AgentLoop(AiClient aiClient, AgentSessionService sessionService, AgentBudget budget) {
        this.aiClient = aiClient;
        this.sessionService = sessionService;
        this.budget = budget;
        this.providerTag = aiClient == null ? "unknown"
                : aiClient.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    public AgentBudget budget() {
        return budget;
    }

    /**
     * Runs the loop until the strategy returns {@link StepDecision.Finish} or
     * the {@link AgentBudget#maxRounds()} cap is reached.
     */
    public LoopOutcome run(AgentRunContext ctx, String initialUserMessage, AgentStrategy strategy) {
        String systemPrompt = strategy.systemPrompt();
        List<AiMessage> history = new ArrayList<>(sessionService.toAiMessages(ctx.session()));
        sessionService.addMessage(ctx.session(), "user", initialUserMessage);
        String currentMessage = initialUserMessage;

        ToolingMode resolvedMode = resolveMode(strategy);
        List<ToolDescriptor> tools = resolvedMode == ToolingMode.NATIVE
                ? strategy.toolDescriptors() : List.of();
        log.debug("AgentLoop starting for issue #{}: provider={}, mode={}, tools={}",
                ctx.issueNumber(), providerTag, resolvedMode, tools.size());

        for (int round = 1; round <= budget.maxRounds(); round++) {
            log.debug("AgentLoop round {}/{} for issue #{}: calling AI (history={} msgs, prompt={} chars, mode={})",
                    round, budget.maxRounds(), ctx.issueNumber(), history.size(),
                    currentMessage == null ? 0 : currentMessage.length(), resolvedMode);

            AgentMetricsHolder.recordToolCallMode(modeTag(resolvedMode), providerTag);

            long started = System.nanoTime();
            ChatTurn turn;
            try {
                if (resolvedMode == ToolingMode.NATIVE) {
                    turn = aiClient.chatWithTools(history, currentMessage, tools, systemPrompt,
                            null, budget.maxTokensPerCall());
                } else {
                    String text = aiClient.chat(history, currentMessage, systemPrompt,
                            null, budget.maxTokensPerCall());
                    turn = ChatTurn.text(text);
                }
            } finally {
                AgentMetricsHolder.recordLatency(modeTag(resolvedMode), providerTag,
                        Duration.ofNanos(System.nanoTime() - started));
            }

            String aiResponse = turn.assistantText();
            log.debug("AgentLoop round {}/{} for issue #{}: AI returned {} chars, {} tool_call(s), reason={}",
                    round, budget.maxRounds(), ctx.issueNumber(),
                    aiResponse == null ? 0 : aiResponse.length(),
                    turn.toolCalls().size(), turn.stopReason());
            sessionService.addMessage(ctx.session(), "assistant", aiResponse);

            StepDecision decision;
            try {
                decision = strategy.step(ctx, turn, round);
            } catch (RuntimeException e) {
                log.error("AgentLoop round {}/{} for issue #{}: strategy.step threw {}: {}",
                        round, budget.maxRounds(), ctx.issueNumber(),
                        e.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
            if (decision instanceof StepDecision.Finish finish) {
                log.debug("AgentLoop round {}/{} for issue #{}: strategy decided FINISH",
                        round, budget.maxRounds(), ctx.issueNumber());
                return finish.outcome();
            }
            log.debug("AgentLoop round {}/{} for issue #{}: strategy decided CONTINUE",
                    round, budget.maxRounds(), ctx.issueNumber());
            String next = ((StepDecision.Continue) decision).nextUserMessage();
            history.add(AiMessage.builder().role("user").content(currentMessage).build());
            history.add(AiMessage.builder().role("assistant").content(aiResponse).build());
            currentMessage = next;
            sessionService.addMessage(ctx.session(), "user", currentMessage);
        }

        log.warn("AgentLoop exhausted {} rounds without final decision (issue #{})",
                budget.maxRounds(), ctx.issueNumber());
        return strategy.onBudgetExhausted(ctx);
    }

    private ToolingMode resolveMode(AgentStrategy strategy) {
        if (strategy.preferredToolMode() != ToolingMode.NATIVE) {
            return ToolingMode.LEGACY;
        }
        if (aiClient == null || !aiClient.supportsNativeTools()) {
            log.debug("Strategy requested NATIVE tools but client {} does not support them; falling back to LEGACY",
                    providerTag);
            return ToolingMode.LEGACY;
        }
        if (strategy.toolDescriptors().isEmpty()) {
            log.debug("Strategy requested NATIVE tools but supplied no descriptors; falling back to LEGACY");
            return ToolingMode.LEGACY;
        }
        return ToolingMode.NATIVE;
    }

    private static String modeTag(ToolingMode mode) {
        return mode == ToolingMode.NATIVE ? "native" : "legacy";
    }
}

