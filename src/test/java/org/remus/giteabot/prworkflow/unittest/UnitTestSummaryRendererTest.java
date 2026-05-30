package org.remus.giteabot.prworkflow.unittest;

import org.junit.jupiter.api.Test;
import org.remus.giteabot.prworkflow.unittest.coverage.CoverageResult;
import org.remus.giteabot.prworkflow.unittest.runner.UnitTestOutcome;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTestSummaryRendererTest {

    private UnitTestSuite suiteWith(UnitTestCaseStatus status) {
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.MAVEN);
        UnitTestCase c = new UnitTestCase();
        c.setPath("src/test/java/FooTest.java");
        c.setLastStatus(status);
        suite.addCase(c);
        return suite;
    }

    @Test
    void rendersPassingSuiteWithCoverageDelta() {
        UnitTestOutcome outcome = UnitTestOutcome.passed("1/1 passed", 1,
                CoverageResult.of(85, 100), "ok");
        String md = UnitTestSummaryRenderer.render(suiteWith(UnitTestCaseStatus.PASSED), outcome,
                CoverageResult.of(80, 100));
        assertThat(md).contains("AI Unit Tests");
        assertThat(md).contains("passed");
        assertThat(md).contains("85.0%");
        assertThat(md).contains("baseline 80.0%");
        assertThat(md).contains("`src/test/java/FooTest.java`");
    }

    @Test
    void rendersFailingSuiteWithRunnerOutput() {
        UnitTestOutcome outcome = UnitTestOutcome.failed("runner exited with code 1", 2, 2,
                CoverageResult.unknown(), "stack trace here");
        String md = UnitTestSummaryRenderer.render(suiteWith(UnitTestCaseStatus.FAILED), outcome,
                CoverageResult.unknown());
        assertThat(md).contains("failed");
        assertThat(md).contains("Runner output");
        assertThat(md).contains("stack trace here");
    }

    @Test
    void renderStartingMentionsFramework() {
        assertThat(UnitTestSummaryRenderer.renderStarting(7, UnitTestFramework.PYTEST))
                .contains("pytest");
    }
}

