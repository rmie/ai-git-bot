package org.remus.giteabot.prworkflow.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkDetectorTest {

    private final FrameworkDetector detector = new FrameworkDetector();

    @Test
    void detectsMavenFromPom(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        assertThat(detector.detect(dir)).contains(UnitTestFramework.MAVEN);
    }

    @Test
    void detectsNpmFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), "{}");
        assertThat(detector.detect(dir)).contains(UnitTestFramework.NPM);
    }

    @Test
    void detectsGoFromGoMod(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("go.mod"), "module x");
        assertThat(detector.detect(dir)).contains(UnitTestFramework.GO);
    }

    @Test
    void detectsPytestFromPyproject(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pyproject.toml"), "[tool.pytest]");
        assertThat(detector.detect(dir)).contains(UnitTestFramework.PYTEST);
    }

    @Test
    void mavenWinsOverNpmInPolyglotRepo(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Files.writeString(dir.resolve("package.json"), "{}");
        assertThat(detector.detect(dir)).contains(UnitTestFramework.MAVEN);
    }

    @Test
    void emptyWhenNoMarker(@TempDir Path dir) {
        assertThat(detector.detect(dir)).isEmpty();
    }
}

