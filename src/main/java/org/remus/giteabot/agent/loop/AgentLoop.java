package org.remus.giteabot.agent.loop;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.agent.session.AgentSessionService;
import org.remus.giteabot.ai.AiClient;
import org.remus.giteabot.ai.AiMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic agent loop that owns the chat/history/session-sync mechanics shared
 * by every agent flavour. The agent-specific decision logic lives in an
 * {@link AgentStrategy}.
 *
 * <p>The loop guarantees the following invariants on every iteration:</p>
 * <ol>
 *     <li>{@code aiClient.chat} is called with the prior history and the
 *         current user message.</li>
 *     <li>The assistant reply is appended to both the in-memory history and
 *         the persisted session.</li>
 *     <li>The strategy is asked to {@link AgentStrategy#step step} on the
 *         response.</li>
 *     <li>If the strategy returns {@link StepDecision.Continue}, the prior
 *         user/assistant pair is committed to the in-memory history and the
 *         next user message is persisted to the session.</li>
 *     <li>If the strategy returns {@link StepDecision.Finish}, the loop
 *         returns the carried outcome.</li>
 * </ol>
 *
 * <p>This replaces the parallel {@code history.add(AiMessage…) / sessionService.addMessage}
 * boilerplate previously duplicated in every agent service.</p>
 */
@Slf4j
public final class AgentLoop {

    private final AiClient aiClient;
    private final AgentSessionService sessionService;
    private final AgentBudget budget;

    public AgentLoop(AiClient aiClient, AgentSessionService sessionService, AgentBudget budget) {
        this.aiClient = aiClient;
        this.sessionService = sessionService;
        this.budget = budget;
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

        for (int round = 1; round <= budget.maxRounds(); round++) {
            log.debug("AgentLoop round {}/{} for issue #{}: calling AI (history={} msgs, prompt={} chars)",
                    round, budget.maxRounds(), ctx.issueNumber(), history.size(),
                    currentMessage == null ? 0 : currentMessage.length());
            String aiResponse = aiClient.chat(history, currentMessage, systemPrompt,
                    null, budget.maxTokensPerCall());
            log.debug("AgentLoop round {}/{} for issue #{}: AI returned {} chars",
                    round, budget.maxRounds(), ctx.issueNumber(),
                    aiResponse == null ? 0 : aiResponse.length());
            sessionService.addMessage(ctx.session(), "assistant", aiResponse);

            StepDecision decision;
            try {
                decision = strategy.step(ctx, aiResponse, round);
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
}

