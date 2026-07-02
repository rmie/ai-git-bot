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

class CargoUnitTestRunnerIT {

    private ToolExecutionService toolExecutionService;
    private UnitTestCaseRepository caseRepository;
    private CoverageParser coverageParser;
    private UnitTestRunner runner;

    @TempDir
    Path workspaceDir;

    @BeforeEach
    void setUp() {
        AgentConfigProperties config = new AgentConfigProperties();
        config.getValidation().setAvailableTools(List.of("cargo", "rustup"));

        toolExecutionService = new ToolExecutionService(config, new ToolCatalog(config));
        caseRepository = mock(UnitTestCaseRepository.class);
        coverageParser = new CoverageParser();

        runner = new UnitTestRunner(toolExecutionService, caseRepository, coverageParser);
    }

    @Test
    void executeCargoSuite_passes() throws IOException {
        org.springframework.util.FileSystemUtils.copyRecursively(Path.of("src/test/resources/rust-cargo"), workspaceDir);

        UnitTestCase testCase = new UnitTestCase();
        testCase.setPath("tests/integration_test.rs");
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.CARGO);
        when(caseRepository.findBySuiteOrderByIdAsc(any())).thenReturn(List.of(testCase));

        // Configure rustup default toolchain for root (since docker runs maven as root but rustup was installed by appuser)
        toolExecutionService.executeTool(workspaceDir, "rustup", List.of("default", "stable"));

        UnitTestRunRequest request = new UnitTestRunRequest(suite, workspaceDir, UnitTestFramework.CARGO, 0, null);

        UnitTestOutcome outcome = runner.run(request);
        assertThat(outcome.status())
                .withFailMessage("Test runner failed: " + outcome.summary() + "\n" + outcome.rawOutput())
                .isEqualTo(UnitTestOutcomeStatus.PASSED);
    }
}
