package org.remus.giteabot.prworkflow.unittest;

import org.remus.giteabot.prworkflow.WorkflowParamName;

/**
 * Compile-time-safe identifiers for the {@link UnitTestWorkflow} parameter
 * keys. Mirrors {@code E2eTestParam} so the workflow-edit UI renders the same
 * familiar controls, but tuned for unit-test generation (no preview env).
 */
public enum UnitTestParam implements WorkflowParamName {

    /** Build/test toolchain key — see {@link UnitTestFramework}. {@code auto} = detect from repo. */
    FRAMEWORK("framework"),

    /** Per-test retry budget (clamped 0..5). */
    MAX_RETRIES("maxRetries"),

    /** Hard cap on the number of generated test cases per suite. */
    MAX_TEST_CASES("maxTestCases"),

    /** Post-run suite handling — reuses {@code SuiteLifecycleMode}. */
    SUITE_LIFECYCLE("suiteLifecycle");

    private final String key;

    UnitTestParam(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}


