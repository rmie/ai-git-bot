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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DotnetUnitTestRunnerIT {

    private ToolExecutionService toolExecutionService;
    private UnitTestCaseRepository caseRepository;
    private CoverageParser coverageParser;
    private UnitTestRunner runner;

    @TempDir
    Path workspaceDir;

    @BeforeEach
    void setUp() {
        AgentConfigProperties config = new AgentConfigProperties();
        config.getValidation().setAvailableTools(List.of("dotnet"));

        toolExecutionService = new ToolExecutionService(config, new ToolCatalog(config));
        caseRepository = mock(UnitTestCaseRepository.class);
        coverageParser = new CoverageParser();

        runner = new UnitTestRunner(toolExecutionService, caseRepository, coverageParser);
    }

    @Test
    void executeDotnetSuite_passes() throws IOException {
        org.springframework.util.FileSystemUtils.copyRecursively(Path.of("src/test/resources/dotnet-xunit"), workspaceDir);

        UnitTestCase testCase = new UnitTestCase();
        testCase.setPath("tests/CalculatorTests.cs");
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.DOTNET);
        when(caseRepository.findBySuiteOrderByIdAsc(any())).thenReturn(List.of(testCase));

        UnitTestRunRequest request = new UnitTestRunRequest(suite, workspaceDir, UnitTestFramework.DOTNET, 0, null);

        UnitTestOutcome outcome = runner.run(request);
        assertThat(outcome.status())
                .withFailMessage("Test runner failed: " + outcome.summary() + "\n" + outcome.rawOutput())
                .isEqualTo(UnitTestOutcomeStatus.PASSED);

        // Verify that Dotnet generated the JUnit XML report natively (if JunitXml.TestLogger was injected by user)
        // Note: For dotnet we usually have to run dotnet test --settings .runsettings
        // We'll just verify the test passes for now. If .runsettings was picked up, dotnet.xml will exist.
        Path dotnetReport = workspaceDir.resolve("TestResults/dotnet.xml");
        if (Files.exists(dotnetReport)) {
            Path hostReportsDir = Path.of("target/failsafe-reports");
            Files.createDirectories(hostReportsDir);
            Files.copy(dotnetReport, hostReportsDir.resolve("TEST-DotnetUnitTestRunnerIT-xunit.xml"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
