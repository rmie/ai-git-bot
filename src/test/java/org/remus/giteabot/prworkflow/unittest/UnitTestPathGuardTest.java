package org.remus.giteabot.prworkflow.unittest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTestPathGuardTest {

    @Test
    void mavenAllowsSrcTestRejectsSrcMain() {
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.MAVEN, "src/test/java/FooTest.java")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.MAVEN, "src/main/java/Foo.java")).isFalse();
    }

    @Test
    void npmRejectsBareSrcButAllowsTestDirsSegmentsAndSuffixes() {
        // The production-code safety bug: bare src/ must be rejected.
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/index.js")).isFalse();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/lib/util.js")).isFalse();
        // Dedicated test roots.
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "test/foo.js")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "tests/foo.js")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "__tests__/foo.js")).isTrue();
        // Nested __tests__ segment (src/**/__tests__/).
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/components/__tests__/Button.js")).isTrue();
        // Co-located filename conventions.
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/components/Button.test.jsx")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/Button.spec.ts")).isTrue();
    }

    @Test
    void cargoAllowsOnlyTestsDirNotSrc() {
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.CARGO, "src/lib.rs")).isFalse();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.CARGO, "src/foo/mod.rs")).isFalse();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.CARGO, "tests/integration.rs")).isTrue();
    }

    @Test
    void goAcceptsTestSuffixAnywhereButNotProductionGoFiles() {
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.GO, "pkg/foo/foo_test.go")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.GO, "main.go")).isFalse();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.GO, "pkg/foo/foo.go")).isFalse();
    }

    @Test
    void dotnetTestDirIsCaseInsensitive() {
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.DOTNET, "Tests/FooTests.cs")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.DOTNET, "tests/FooTests.cs")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.DOTNET, "src/Foo.cs")).isFalse();
    }

    @Test
    void leadingDotSlashAndBackslashesAreNormalised() {
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.MAVEN, "./src/test/java/FooTest.java")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src\\components\\__tests__\\Button.js")).isTrue();
    }

    @Test
    void nullAndBlankAreRejected() {
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.MAVEN, null)).isFalse();
        assertThat(UnitTestPathGuard.isAllowedTestPath(null, "tests/foo.js")).isFalse();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.MAVEN, "   ")).isFalse();
    }
}

