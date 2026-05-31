package org.remus.giteabot.prworkflow.unittest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitTestFrameworkTest {

    @Test
    void fromKeyAcceptsKeyAndEnumName() {
        assertThat(UnitTestFramework.fromKey("maven")).isEqualTo(UnitTestFramework.MAVEN);
        assertThat(UnitTestFramework.fromKey("MAVEN")).isEqualTo(UnitTestFramework.MAVEN);
        assertThat(UnitTestFramework.fromKey("pytest")).isEqualTo(UnitTestFramework.PYTEST);
        assertThat(UnitTestFramework.fromKey("go")).isEqualTo(UnitTestFramework.GO);
    }

    @Test
    void fromKeyRejectsUnknownAndNull() {
        assertThatThrownBy(() -> UnitTestFramework.fromKey("rust"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnitTestFramework.fromKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyFrameworkHasARunCommandAndTestLocationRule() {
        for (UnitTestFramework f : UnitTestFramework.values()) {
            assertThat(f.defaultRunCommand()).isNotEmpty();
            // Every framework must define at least one way to recognise a test
            // location — a dedicated test-root prefix, a test directory segment,
            // or a test filename suffix (Go relies solely on *_test.go).
            boolean hasRule = !f.allowedTestPrefixes().isEmpty()
                    || !f.allowedTestPathSegments().isEmpty()
                    || !f.allowedTestFileSuffixes().isEmpty();
            assertThat(hasRule)
                    .as("framework %s must define a test-location rule", f)
                    .isTrue();
        }
    }

    @Test
    void productionSourceRootsAreNeverWritableTestLocations() {
        // Regression guard: bare production source roots must never be accepted
        // as test locations (npm/cargo previously allowed src/).
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/index.js")).isFalse();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.CARGO, "src/lib.rs")).isFalse();
        // But genuine co-located / dedicated test files are still allowed.
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/util/__tests__/foo.js")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.NPM, "src/foo.test.ts")).isTrue();
        assertThat(UnitTestPathGuard.isAllowedTestPath(UnitTestFramework.CARGO, "tests/integration.rs")).isTrue();
    }
}

