package org.remus.giteabot.prworkflow.unittest.runner;

/**
 * Overall status of one {@link UnitTestRunner} execution. Mapped onto
 * {@link org.remus.giteabot.prworkflow.WorkflowResult} by {@code UnitTestWorkflow}.
 */
public enum UnitTestOutcomeStatus {
    /** Every executed case passed. */
    PASSED,
    /** At least one case failed. */
    FAILED,
    /** Runner refused to run (unsupported framework, nothing generated, …). */
    SKIPPED,
    /** Runner aborted before completion (timeout, compilation error, infra error). */
    ERROR
}

