package org.remus.giteabot.prworkflow.unittest;

/**
 * Outcome of one {@link UnitTestCase}'s last execution. Persisted on
 * {@code unit_test_cases.last_status}. The string {@link #name()} is what hits
 * the database — renaming a value requires a Flyway migration.
 */
public enum UnitTestCaseStatus {
    /** Generated but not yet executed (initial value). */
    PENDING,
    /** The test executed and passed. */
    PASSED,
    /** The test executed and failed. */
    FAILED,
    /** Passed only after one or more retries. */
    FLAKY,
    /** Skipped by the runner. */
    SKIPPED,
    /** Could not be executed at all (compilation / framework error). */
    ERROR
}

