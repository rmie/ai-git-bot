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
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PythonUnitTestRunnerIT {

    private ToolExecutionService toolExecutionService;
    private UnitTestCaseRepository caseRepository;
    private CoverageParser coverageParser;
    private UnitTestRunner runner;

    @TempDir
    Path workspaceDir;

    @BeforeEach
    void setUp() {
        AgentConfigProperties config = new AgentConfigProperties();
        config.getValidation().setAvailableTools(List.of("python3", "pip3"));

        toolExecutionService = new ToolExecutionService(config, new ToolCatalog(config));
        caseRepository = mock(UnitTestCaseRepository.class);
        coverageParser = new CoverageParser();

        runner = new UnitTestRunner(toolExecutionService, caseRepository, coverageParser);
    }

    @Test
    void executePytestSuite_installsDependenciesAndPasses() throws IOException {
        // 1. Copy the Fixtures
        org.springframework.util.FileSystemUtils.copyRecursively(Path.of("src/test/resources/python-pytest"), workspaceDir);

        // Mock the DB repository to return our one test case
        UnitTestCase testCase = new UnitTestCase();
        testCase.setPath("tests/test_calculator.py");
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.PYTEST);
        when(caseRepository.findBySuiteOrderByIdAsc(any())).thenReturn(List.of(testCase));

        UnitTestRunRequest request = new UnitTestRunRequest(suite, workspaceDir, UnitTestFramework.PYTEST, 0, null);

        // 2. Install dependencies (pip3 install)
        ToolResult installResult = toolExecutionService.executeTool(workspaceDir, "pip3", List.of("install", "-r", "requirements.txt"));
        assertThat(installResult.success())
                .withFailMessage("pip3 install failed: " + installResult.error() + "\n" + installResult.output())
                .isTrue();

        // 3. Assert Execution (Positive Test)
        UnitTestOutcome postInstallOutcome = runner.run(request);
        assertThat(postInstallOutcome.status())
                .withFailMessage("Test runner failed: " + postInstallOutcome.summary() + "\n" + postInstallOutcome.rawOutput())
                .isEqualTo(UnitTestOutcomeStatus.PASSED);

        // Verify coverage parsing worked (cobertura.xml)
        assertThat(postInstallOutcome.coverage().known()).isTrue();
        assertThat(postInstallOutcome.coverage().coveredLines()).isGreaterThan(0);

        // Verify that Pytest generated the JUnit XML report and integrate it into the host build
        Path pytestReport = workspaceDir.resolve("pytest.xml");
        assertThat(Files.exists(pytestReport))
                .withFailMessage("Pytest JUnit XML report was not generated!")
                .isTrue();

        Path hostReportsDir = Path.of("target/failsafe-reports");
        Files.createDirectories(hostReportsDir);
        Files.copy(pytestReport, hostReportsDir.resolve("TEST-PythonUnitTestRunnerIT-pytest.xml"), StandardCopyOption.REPLACE_EXISTING);
    }
}
