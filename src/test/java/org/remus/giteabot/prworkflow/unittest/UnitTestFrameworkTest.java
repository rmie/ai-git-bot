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
    void everyFrameworkHasARunCommandAndPrefixes() {
        for (UnitTestFramework f : UnitTestFramework.values()) {
            assertThat(f.defaultRunCommand()).isNotEmpty();
            assertThat(f.allowedTestPrefixes()).isNotEmpty();
        }
    }
}

