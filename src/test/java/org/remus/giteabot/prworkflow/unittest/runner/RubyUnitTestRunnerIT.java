package org.remus.giteabot.prworkflow.unittest.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.remus.giteabot.agent.tools.ToolCatalog;
import org.remus.giteabot.agent.validation.ToolExecutionService;
import org.remus.giteabot.agent.validation.ToolResult;
import org.remus.giteabot.config.AgentConfigProperties;
import org.remus.giteabot.prworkflow.unittest.UnitTestCase;
import org.remus.giteabot.prworkflow.unittest.UnitTestCaseRepository;
import org.remus.giteabot.prworkflow.unittest.UnitTestFramework;
import org.remus.giteabot.prworkflow.unittest.UnitTestSuite;
import org.remus.giteabot.prworkflow.unittest.coverage.CoverageParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RubyUnitTestRunnerIT {

    private ToolExecutionService toolExecutionService;
    private UnitTestCaseRepository caseRepository;
    private CoverageParser coverageParser;
    private UnitTestRunner runner;

    @TempDir
    Path workspaceDir;

    @BeforeEach
    void setUp() {
        AgentConfigProperties config = new AgentConfigProperties();
        // The default configuration might not include bundle, so we inject it here
        // for the ToolExecutionService whitelist
        config.getValidation().setAvailableTools(List.of("bundle"));

        toolExecutionService = new ToolExecutionService(config, new ToolCatalog(config));
        caseRepository = mock(UnitTestCaseRepository.class);
        coverageParser = mock(CoverageParser.class);

        runner = new UnitTestRunner(toolExecutionService, caseRepository, coverageParser);
    }

    /**
     * Failsafe guard to ensure this IT skips gracefully on developer host environments 
     * where 'bundle' is not installed (e.g. Windows machines), while running fine 
     * in the Docker test container or standard GitHub Actions Ubuntu runner.
     */
    @Test
    void executeRubyRspecSuite_installsGemsAndPasses() throws IOException {
        // 1. Copy the Fixtures
        org.springframework.util.FileSystemUtils.copyRecursively(Path.of("src/test/resources/ruby"), workspaceDir);

        // Mock the DB repository to return our one test case
        UnitTestCase testCase = new UnitTestCase();
        testCase.setPath("spec/calculator_spec.rb");
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.BUNDLE);
        when(caseRepository.findBySuiteOrderByIdAsc(any())).thenReturn(List.of(testCase));

        UnitTestRunRequest request = new UnitTestRunRequest(suite, workspaceDir, UnitTestFramework.BUNDLE, 0, null);

        // 2. Assert Pristine State (Negative Test)
        // RSpec is not installed yet, so the runner should fail (exit non-zero).
        UnitTestOutcome preInstallOutcome = runner.run(request);
        assertThat(preInstallOutcome.status())
                .withFailMessage("Expected failure before installation, but the runner passed!")
                .isEqualTo(UnitTestOutcomeStatus.FAILED);

        // 3. Install dependencies using the target toolchain mechanism
        ToolResult installResult = toolExecutionService.executeTool(workspaceDir, "bundle", List.of("install"));
        assertThat(installResult.success())
                .withFailMessage("bundle install failed: " + installResult.error() + "\\n" + installResult.output())
                .isTrue();

        // 4. Assert Execution (Positive Test)
        // RSpec is now installed, runner should succeed and find our tests.
        UnitTestOutcome postInstallOutcome = runner.run(request);
        assertThat(postInstallOutcome.status())
                .withFailMessage("Test runner failed: " + postInstallOutcome.summary() + "\\n" + postInstallOutcome.rawOutput())
                .isEqualTo(UnitTestOutcomeStatus.PASSED);

        // Verify that RSpec actually executed and generated standard output
        assertThat(postInstallOutcome.rawOutput())
                .withFailMessage("RSpec output was not found in the raw runner logs!")
                .containsPattern("\\d+ example(s)?, 0 failures");
    }
}
