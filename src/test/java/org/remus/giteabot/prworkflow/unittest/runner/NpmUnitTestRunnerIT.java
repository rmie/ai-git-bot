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

class NpmUnitTestRunnerIT {

    private ToolExecutionService toolExecutionService;
    private UnitTestCaseRepository caseRepository;
    private CoverageParser coverageParser;
    private UnitTestRunner runner;

    @TempDir
    Path workspaceDir;

    @BeforeEach
    void setUp() {
        AgentConfigProperties config = new AgentConfigProperties();
        config.getValidation().setAvailableTools(List.of("npm"));

        toolExecutionService = new ToolExecutionService(config, new ToolCatalog(config));
        caseRepository = mock(UnitTestCaseRepository.class);
        coverageParser = new CoverageParser();

        runner = new UnitTestRunner(toolExecutionService, caseRepository, coverageParser);
    }

    @Test
    void executeNpmJestSuite_installsDependenciesAndPasses() throws IOException {
        // 1. Copy the Fixtures
        org.springframework.util.FileSystemUtils.copyRecursively(Path.of("src/test/resources/npm-jest"), workspaceDir);

        // Mock the DB repository to return our one test case
        UnitTestCase testCase = new UnitTestCase();
        testCase.setPath("__tests__/calculator.test.js");
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.NPM);
        when(caseRepository.findBySuiteOrderByIdAsc(any())).thenReturn(List.of(testCase));

        UnitTestRunRequest request = new UnitTestRunRequest(suite, workspaceDir, UnitTestFramework.NPM, 0, null);

        // 2. Install dependencies (npm install)
        ToolResult installResult = toolExecutionService.executeTool(workspaceDir, "npm", List.of("install", "--silent"));
        assertThat(installResult.success())
                .withFailMessage("npm install failed: " + installResult.error() + "\n" + installResult.output())
                .isTrue();

        // 3. Assert Execution (Positive Test)
        UnitTestOutcome postInstallOutcome = runner.run(request);
        assertThat(postInstallOutcome.status())
                .withFailMessage("Test runner failed: " + postInstallOutcome.summary() + "\n" + postInstallOutcome.rawOutput())
                .isEqualTo(UnitTestOutcomeStatus.PASSED);

        // Verify coverage parsing worked (lcov.info)
        assertThat(postInstallOutcome.coverage().known()).isTrue();
        assertThat(postInstallOutcome.coverage().coveredLines()).isGreaterThan(0);

        // Verify that Jest generated the JUnit XML report and integrate it into the host build
        Path jestReport = workspaceDir.resolve("jest.xml");
        assertThat(Files.exists(jestReport))
                .withFailMessage("Jest JUnit XML report was not generated!")
                .isTrue();

        Path hostReportsDir = Path.of("target/failsafe-reports");
        Files.createDirectories(hostReportsDir);
        Files.copy(jestReport, hostReportsDir.resolve("TEST-NpmUnitTestRunnerIT-jest.xml"), StandardCopyOption.REPLACE_EXISTING);
    }
}
