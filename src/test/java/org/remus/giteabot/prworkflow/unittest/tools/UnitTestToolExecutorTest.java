package org.remus.giteabot.prworkflow.unittest.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.remus.giteabot.prworkflow.unittest.UnitTestCase;
import org.remus.giteabot.prworkflow.unittest.UnitTestCaseRepository;
import org.remus.giteabot.prworkflow.unittest.UnitTestFramework;
import org.remus.giteabot.prworkflow.unittest.UnitTestSuite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnitTestToolExecutorTest {

    private final UnitTestCaseRepository caseRepository = mock(UnitTestCaseRepository.class);
    private final UnitTestToolExecutor executor = new UnitTestToolExecutor(caseRepository);

    private UnitTestToolContext ctx(Path workspace, UnitTestFramework framework) {
        return new UnitTestToolContext(new UnitTestSuite(), workspace, framework);
    }

    @Test
    void writesFileUnderAllowedMavenTestDir(@TempDir Path workspace) throws Exception {
        when(caseRepository.findBySuiteAndPath(any(), any())).thenReturn(Optional.empty());
        when(caseRepository.save(any())).thenAnswer(inv -> {
            UnitTestCase c = inv.getArgument(0);
            c.setId(7L);
            return c;
        });

        String result = executor.execute("unit-test-write", Map.of(
                "path", "src/test/java/com/acme/FooTest.java",
                "content", "class FooTest {}"), ctx(workspace, UnitTestFramework.MAVEN));

        assertThat(result).startsWith("OK");
        assertThat(Files.readString(workspace.resolve("src/test/java/com/acme/FooTest.java")))
                .isEqualTo("class FooTest {}");
    }

    @Test
    void rejectsWriteOutsideTestDirectory(@TempDir Path workspace) {
        String result = executor.execute("unit-test-write", Map.of(
                "path", "src/main/java/com/acme/Foo.java",
                "content", "evil"), ctx(workspace, UnitTestFramework.MAVEN));

        assertThat(result).startsWith("ERROR");
        assertThat(Files.exists(workspace.resolve("src/main/java/com/acme/Foo.java"))).isFalse();
    }

    @Test
    void rejectsPathTraversal(@TempDir Path workspace) {
        String result = executor.execute("unit-test-write", Map.of(
                "path", "src/test/../../escape.java",
                "content", "x"), ctx(workspace, UnitTestFramework.MAVEN));
        assertThat(result).startsWith("ERROR");
    }

    @Test
    void goAllowsTestFilesNextToCode(@TempDir Path workspace) {
        when(caseRepository.findBySuiteAndPath(any(), any())).thenReturn(Optional.empty());
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String ok = executor.execute("unit-test-write", Map.of(
                "path", "internal/foo/foo_test.go",
                "content", "package foo"), ctx(workspace, UnitTestFramework.GO));
        assertThat(ok).startsWith("OK");

        String rejected = executor.execute("unit-test-write", Map.of(
                "path", "internal/foo/foo.go",
                "content", "package foo"), ctx(workspace, UnitTestFramework.GO));
        assertThat(rejected).startsWith("ERROR");
    }

    @Test
    void unknownToolReturnsError(@TempDir Path workspace) {
        assertThat(executor.execute("nope", Map.of(), ctx(workspace, UnitTestFramework.MAVEN)))
                .startsWith("ERROR");
    }
}

