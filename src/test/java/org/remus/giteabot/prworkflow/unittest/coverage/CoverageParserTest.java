package org.remus.giteabot.prworkflow.unittest.coverage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.remus.giteabot.prworkflow.unittest.UnitTestFramework;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CoverageParserTest {

    private final CoverageParser parser = new CoverageParser();

    @Test
    void parsesJacocoLineCounter(@TempDir Path workspace) throws Exception {
        Path report = workspace.resolve("target/site/jacoco/jacoco.xml");
        Files.createDirectories(report.getParent());
        Files.writeString(report, """
                <report name="x">
                  <counter type="INSTRUCTION" missed="10" covered="90"/>
                  <counter type="LINE" missed="20" covered="80"/>
                </report>""");

        CoverageResult result = parser.parse(workspace, UnitTestFramework.MAVEN, "");
        assertThat(result.known()).isTrue();
        assertThat(result.percent()).isCloseTo(80.0, within(0.01));
    }

    @Test
    void parsesGoStdoutPercentage(@TempDir Path workspace) {
        CoverageResult result = parser.parse(workspace, UnitTestFramework.GO,
                "ok  example  0.1s  coverage: 73.5% of statements");
        assertThat(result.known()).isTrue();
        assertThat(result.percent()).isCloseTo(73.5, within(0.1));
    }

    @Test
    void parsesLcovInfo(@TempDir Path workspace) throws Exception {
        Path report = workspace.resolve("coverage/lcov.info");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "SF:foo.js\nLF:10\nLH:5\nend_of_record\n");
        CoverageResult result = parser.parse(workspace, UnitTestFramework.NPM, "");
        assertThat(result.known()).isTrue();
        assertThat(result.percent()).isCloseTo(50.0, within(0.01));
    }

    @Test
    void returnsUnknownWhenNoReport(@TempDir Path workspace) {
        assertThat(parser.parse(workspace, UnitTestFramework.MAVEN, "").known()).isFalse();
    }
}

