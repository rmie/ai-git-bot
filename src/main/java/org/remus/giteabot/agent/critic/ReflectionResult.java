package org.remus.giteabot.agent.critic;

/**
 * Step 7.3 — outcome of one Critic / Reflection invocation.
 *
 * <p>Three outcomes are mapped 1:1 to a control-flow decision in the calling
 * agent loop:</p>
 * <ul>
 *     <li>{@link Outcome#APPROVE} — proceed to commit / push.</li>
 *     <li>{@link Outcome#ITERATE} — feed the {@link #feedback()} back into the
 *         loop just like a validation failure (counts towards the budget's
 *         {@code maxRounds} cap).</li>
 *     <li>{@link Outcome#ABORT} — fail fast and post the feedback as a
 *         comment on the issue.</li>
 *     <li>{@link Outcome#SKIPPED} — only used internally when the critic is
 *         disabled; treated as APPROVE by callers but published as a
 *         distinct metric tag for observability.</li>
 * </ul>
 *
 * @param outcome  one of {@link Outcome}
 * @param feedback short, actionable, English text shown to the agent or user
 */
public record ReflectionResult(Outcome outcome, String feedback) {

    public enum Outcome {
        APPROVE,
        ITERATE,
        ABORT,
        SKIPPED
    }

    public static ReflectionResult skipped() {
        return new ReflectionResult(Outcome.SKIPPED, "Critic disabled");
    }

    public static ReflectionResult approve(String feedback) {
        return new ReflectionResult(Outcome.APPROVE, feedback);
    }

    public static ReflectionResult iterate(String feedback) {
        return new ReflectionResult(Outcome.ITERATE, feedback);
    }

    public static ReflectionResult abort(String feedback) {
        return new ReflectionResult(Outcome.ABORT, feedback);
    }
}
