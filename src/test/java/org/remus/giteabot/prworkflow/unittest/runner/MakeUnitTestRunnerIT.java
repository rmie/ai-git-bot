package org.remus.giteabot.prworkflow.unittest.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.remus.giteabot.agent.tools.ToolCatalog;
import org.remus.giteabot.agent.validation.ToolExecutionService;
import org.remus.giteabot.config.AgentConfigProperties;
import org.remus.giteabot.prworkflow.unittest.UnitTestCase;
import org.remus.giteabot.prworkflow.unittest.UnitTestCaseRepository;
import org.remus.giteabot.prworkflow.unittest.UnitTestFramework;
import org.remus.giteabot.prworkflow.unittest.UnitTestSuite;
import org.remus.giteabot.prworkflow.unittest.coverage.CoverageParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MakeUnitTestRunnerIT {

    private ToolExecutionService toolExecutionService;
    private UnitTestCaseRepository caseRepository;
    private CoverageParser coverageParser;
    private UnitTestRunner runner;

    @TempDir
    Path workspaceDir;

    @BeforeEach
    void setUp() {
        AgentConfigProperties config = new AgentConfigProperties();
        config.getValidation().setAvailableTools(List.of("make"));

        toolExecutionService = new ToolExecutionService(config, new ToolCatalog(config));
        caseRepository = mock(UnitTestCaseRepository.class);
        coverageParser = new CoverageParser();

        runner = new UnitTestRunner(toolExecutionService, caseRepository, coverageParser);
    }

    @Test
    void executeMakeSuite_passes() throws IOException {
        org.springframework.util.FileSystemUtils.copyRecursively(Path.of("src/test/resources/make-gcc"), workspaceDir);

        UnitTestCase testCase = new UnitTestCase();
        testCase.setPath("tests/test.c");
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.MAKE);
        when(caseRepository.findBySuiteOrderByIdAsc(any())).thenReturn(List.of(testCase));

        UnitTestRunRequest request = new UnitTestRunRequest(suite, workspaceDir, UnitTestFramework.MAKE, 0, null);

        UnitTestOutcome outcome = runner.run(request);
        assertThat(outcome.status())
                .withFailMessage("Test runner failed: " + outcome.summary() + "\n" + outcome.rawOutput())
                .isEqualTo(UnitTestOutcomeStatus.PASSED);

        assertThat(outcome.coverage().known()).isTrue();
    }
}
