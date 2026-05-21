package org.remus.giteabot.agent.loop;

/**
 * Budget for one {@link AgentLoop} run.
 *
 * <p>Replaces the previously scattered counters
 * ({@code attempt}, {@code toolRounds}, {@code fileRequestRounds}) by a single
 * record that gives the loop a hard upper bound. Strategies may apply tighter
 * sub-budgets internally, but they cannot exceed {@link #maxRounds()}.</p>
 *
 * @param maxRounds              total iterations of the chat-decide-act cycle.
 *                               Must be &ge; the strategy's own internal limits
 *                               (validation retries + context rounds, etc.).
 * @param maxContextRounds       informational hint for strategies that limit
 *                               context-fetching rounds separately. The loop
 *                               itself does not enforce this.
 * @param maxValidationRetries   informational hint for strategies that retry
 *                               failed validations. The loop itself does not
 *                               enforce this.
 * @param maxTokensPerCall       passed to {@code aiClient.chat} for every call.
 */
public record AgentBudget(int maxRounds,
                          int maxContextRounds,
                          int maxValidationRetries,
                          int maxTokensPerCall) {
}

