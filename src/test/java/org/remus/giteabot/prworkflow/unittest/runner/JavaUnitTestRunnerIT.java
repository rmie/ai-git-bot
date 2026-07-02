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
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JavaUnitTestRunnerIT {

    private ToolExecutionService toolExecutionService;
    private UnitTestCaseRepository caseRepository;
    private CoverageParser coverageParser;
    private UnitTestRunner runner;

    @TempDir
    Path workspaceDir;

    @BeforeEach
    void setUp() {
        AgentConfigProperties config = new AgentConfigProperties();
        // Whitelist mvn for ToolExecutionService
        config.getValidation().setAvailableTools(List.of("mvn", "./mvnw"));

        toolExecutionService = new ToolExecutionService(config, new ToolCatalog(config));
        caseRepository = mock(UnitTestCaseRepository.class);
        coverageParser = mock(CoverageParser.class);

        runner = new UnitTestRunner(toolExecutionService, caseRepository, coverageParser);
    }

    @Test
    void executeJavaMavenSpockSuite_resolvesDependenciesAndPasses() throws IOException {
        // 1. Copy the Java/Maven Fixture
        org.springframework.util.FileSystemUtils.copyRecursively(Path.of("src/test/resources/java-maven"), workspaceDir);

        // Mock the DB repository to return our Spock test case
        UnitTestCase testCase = new UnitTestCase();
        testCase.setPath("src/test/groovy/com/example/CalculatorSpec.groovy");
        UnitTestSuite suite = new UnitTestSuite();
        suite.setFramework(UnitTestFramework.MAVEN);
        when(caseRepository.findBySuiteOrderByIdAsc(any())).thenReturn(List.of(testCase));

        UnitTestRunRequest request = new UnitTestRunRequest(suite, workspaceDir, UnitTestFramework.MAVEN, 0, null);

        // Force Maven to use an isolated local repository inside the temporary workspace
        // This ensures the integration test doesn't cheat by using the shared Docker cache volume
        Path mvnDir = workspaceDir.resolve(".mvn");
        java.nio.file.Files.createDirectories(mvnDir);
        java.nio.file.Files.writeString(mvnDir.resolve("maven.config"), "-Dmaven.repo.local=.m2-repo");

        // 2. Explicitly Resolve/Install Dependencies using the AI Tool Execution
        // This answers the question: "How do we explicitly install dependencies?"
        ToolResult installResult = toolExecutionService.executeTool(workspaceDir, "mvn", List.of("-B", "test-compile"));
        assertThat(installResult.success())
                .withFailMessage("mvn test-compile failed: " + installResult.error() + "\\n" + installResult.output())
                .isTrue();

        // Verify that it actually downloaded things into our isolated repo
        assertThat(java.nio.file.Files.exists(workspaceDir.resolve(".m2-repo/org/spockframework"))).isTrue();

        // 3. Assert Execution
        // The runner executes `mvn -q -B test`. Since we already test-compiled, it should fly through.
        UnitTestOutcome outcome = runner.run(request);
        assertThat(outcome.status())
                .withFailMessage("Test runner failed: " + outcome.summary() + "\\n" + outcome.rawOutput())
                .isEqualTo(UnitTestOutcomeStatus.PASSED);

        // Verify that Spock AND JUnit actually executed by checking the generated Surefire XML reports
        assertThat(java.nio.file.Files.exists(workspaceDir.resolve("target/surefire-reports/TEST-com.example.CalculatorTest.xml")))
                .withFailMessage("JUnit test was not executed!")
                .isTrue();
        assertThat(java.nio.file.Files.exists(workspaceDir.resolve("target/surefire-reports/TEST-com.example.CalculatorSpec.xml")))
                .withFailMessage("Spock test was not executed!")
                .isTrue();
    }
}
