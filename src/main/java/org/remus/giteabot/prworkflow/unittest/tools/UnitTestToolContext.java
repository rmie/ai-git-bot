package org.remus.giteabot.prworkflow.unittest.tools;

import org.remus.giteabot.prworkflow.unittest.UnitTestFramework;
import org.remus.giteabot.prworkflow.unittest.UnitTestSuite;

import java.nio.file.Path;

/**
 * Per-invocation context the {@link UnitTestToolExecutor} needs to write one
 * generated test file into the repository checkout and persist its
 * {@link org.remus.giteabot.prworkflow.unittest.UnitTestCase} row.
 *
 * @param suite     the suite the author agent is populating
 * @param workspace the repository checkout (PR head branch) — writes are
 *                  sandboxed to stay inside it
 * @param framework the resolved toolchain (drives the allowed test directories)
 */
public record UnitTestToolContext(
        UnitTestSuite suite,
        Path workspace,
        UnitTestFramework framework) {
}

