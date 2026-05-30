package org.remus.giteabot.prworkflow.unittest.runner;

import org.remus.giteabot.prworkflow.unittest.coverage.CoverageResult;

/**
 * Aggregate outcome of one {@link UnitTestRunner} invocation. Per-case details
 * live on the persisted {@link org.remus.giteabot.prworkflow.unittest.UnitTestCase}
 * rows; this record is the workflow-level rollup that {@code UnitTestWorkflow}
 * maps onto {@link org.remus.giteabot.prworkflow.WorkflowResult}.
 *
 * @param status    overall outcome
 * @param summary   one-line human-readable summary
 * @param attempted number of test cases the runner actually executed
 * @param failed    number of cases that ended in FAILED or ERROR
 * @param coverage  best-effort coverage information (never {@code null})
 * @param rawOutput truncated stdout/stderr of the runner (for the PR comment)
 */
public record UnitTestOutcome(
        UnitTestOutcomeStatus status,
        String summary,
        int attempted,
        int failed,
        CoverageResult coverage,
        String rawOutput) {

    public UnitTestOutcome {
        coverage = coverage == null ? CoverageResult.unknown() : coverage;
    }

    public static UnitTestOutcome skipped(String summary) {
        return new UnitTestOutcome(UnitTestOutcomeStatus.SKIPPED, summary, 0, 0, CoverageResult.unknown(), null);
    }

    public static UnitTestOutcome error(String summary) {
        return new UnitTestOutcome(UnitTestOutcomeStatus.ERROR, summary, 0, 0, CoverageResult.unknown(), null);
    }

    public static UnitTestOutcome passed(String summary, int attempted, CoverageResult coverage, String rawOutput) {
        return new UnitTestOutcome(UnitTestOutcomeStatus.PASSED, summary, attempted, 0, coverage, rawOutput);
    }

    public static UnitTestOutcome failed(String summary, int attempted, int failed,
                                         CoverageResult coverage, String rawOutput) {
        return new UnitTestOutcome(UnitTestOutcomeStatus.FAILED, summary, attempted, failed, coverage, rawOutput);
    }
}

