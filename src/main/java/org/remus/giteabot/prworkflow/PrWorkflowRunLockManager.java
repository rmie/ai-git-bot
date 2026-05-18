package org.remus.giteabot.prworkflow;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * JVM-local mutual exclusion for the start-of-run critical section of
 * {@link PrWorkflowOrchestrator}.
 *
 * <p>Webhook deliveries are dispatched via {@code @Async} in
 * {@link org.remus.giteabot.admin.BotWebhookService}, which means two
 * back-to-back {@code synchronize} events for the same pull request can
 * execute their orchestrator entrypoints in parallel. Without serialization,
 * both threads would observe "no active runs", both create a
 * {@code RUNNING} row and both proceed to post a review — defeating the
 * cancel-on-resync guarantee.</p>
 *
 * <p>This manager hands out a {@link ReentrantLock} per
 * {@code (botId, owner, repo, prNumber, workflowKey)} tuple. The lock is
 * acquired <em>outside</em> the transactional {@code start()} method so the
 * critical section spans <strong>both</strong> the cancel-existing-rows step
 * <em>and</em> the insert-new-row step. The lock is released only after the
 * transaction has committed — preventing a second thread from observing
 * the new row as still-active before it is visible.</p>
 *
 * <h2>Scope and limitations</h2>
 * <ul>
 *     <li><strong>Single-instance only.</strong> A multi-instance deployment
 *     bypasses this lock entirely. Cross-instance serialization is left to
 *     a future milestone (a partial unique index or an advisory-lock-backed
 *     manager); the current gateway is documented as single-instance.</li>
 *     <li>Lock entries are kept indefinitely in the map. The keyspace is
 *     bounded by {@code (bots × repos × open PRs × workflows)}, which is
 *     small enough to ignore.</li>
 * </ul>
 */
@Component
public class PrWorkflowRunLockManager {

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Executes {@code action} while holding the per-tuple lock. The lock is
     * always released, even when the action throws.
     */
    public <T> T withLock(Long botId, String owner, String repo, Long prNumber, String workflowKey,
                          Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        ReentrantLock lock = locks.computeIfAbsent(buildKey(botId, owner, repo, prNumber, workflowKey),
                key -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** Visible for testing. */
    String buildKey(Long botId, String owner, String repo, Long prNumber, String workflowKey) {
        return (botId == null ? "0" : botId.toString())
                + "|" + safe(owner)
                + "|" + safe(repo)
                + "|" + (prNumber == null ? "0" : prNumber.toString())
                + "|" + safe(workflowKey);
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
