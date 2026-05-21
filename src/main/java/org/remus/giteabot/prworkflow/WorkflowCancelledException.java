package org.remus.giteabot.prworkflow;

/**
 * Thrown by workflow implementations (or by callers checking
 * {@link PrWorkflowContext#isCancelled()}) when the orchestrator has marked
 * the current run as superseded by a newer one for the same pull request.
 *
 * <p>The {@link PrWorkflowOrchestrator} catches this exception specifically
 * and persists the run as {@link PrWorkflowRunStatus#CANCELLED} — it is not
 * treated as a failure, no error is recorded against the bot and no
 * {@code FAILED} metric is emitted.</p>
 */
public class WorkflowCancelledException extends RuntimeException {

    public WorkflowCancelledException(String message) {
        super(message);
    }
}
