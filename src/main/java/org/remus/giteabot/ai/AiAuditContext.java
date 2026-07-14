package org.remus.giteabot.ai;

/**
 * Thread-local context carrying the logical session identifier (e.g.
 * {@code owner/repo#42}), the repository slug ({@code owner/repo-name}) and
 * the activity type (e.g. {@code "review"}, {@code "coding-agent"}) of the
 * work currently being processed, so that AI usage and error records can be
 * correlated with the originating session.
 *
 * <p>Callers that orchestrate AI interactions (webhook handlers, PR workflows)
 * set the session id at the start of processing and must clear it in a
 * {@code finally} block.</p>
 */
public final class AiAuditContext {

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> REPO = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTIVITY_TYPE = new ThreadLocal<>();

    private AiAuditContext() {
    }

    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    public static String getSessionId() {
        return SESSION_ID.get();
    }

    public static void setRepo(String repo) {
        REPO.set(repo);
    }

    public static String getRepo() {
        return REPO.get();
    }

    public static void setActivityType(String activityType) {
        ACTIVITY_TYPE.set(activityType);
    }

    public static String getActivityType() {
        return ACTIVITY_TYPE.get();
    }

    public static void clear() {
        SESSION_ID.remove();
        REPO.remove();
        ACTIVITY_TYPE.remove();
    }
}

