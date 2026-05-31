package org.remus.giteabot.prworkflow.unittest.agents;

import org.remus.giteabot.prworkflow.unittest.UnitTestFramework;
import org.remus.giteabot.systemsettings.SystemPrompt;

/**
 * Central location for the {@code UnitTestAuthorAgent} system prompt.
 *
 * <p>Follows the same two-layer split as {@code E2ePromptLibrary}:</p>
 * <ol>
 *   <li>An <i>editable</i> role description (operator-edited via the System
 *       settings UI) with a hard-coded fallback here.</li>
 *   <li>A non-editable <i>protocol suffix</i> appended by the software that
 *       pins the active {@link UnitTestFramework} key, the allowed test
 *       directories and the {@code unit-test-write} tool contract.</li>
 * </ol>
 *
 * <p>The tool-call JSON protocol is additionally appended by
 * {@link org.remus.giteabot.agent.shared.SystemPromptAssembler} from the
 * shared {@link org.remus.giteabot.agent.tools.ToolCatalog}.</p>
 */
public final class UnitTestPromptLibrary {

    public static final String FRAMEWORK_PLACEHOLDER = "{framework}";
    public static final String TEST_DIRS_PLACEHOLDER = "{testDirs}";

    private static final String SECTION_SEPARATOR = "\n\n";

    private UnitTestPromptLibrary() {
        // utility
    }

    /**
     * Built-in default editable role description for the author. Seeded into
     * the database by Flyway migration {@code V23} so operators see exactly
     * this text in the System settings UI on a fresh install.
     */
    public static final String DEFAULT_AUTHOR_EDITABLE = """
            You are UnitTestAuthorAgent, an automated white-box test writer that
            runs on every opened or synchronised pull request. The user message
            gives you the PR title, body, the unified diff and the full content
            of the changed production files.

            Your job is to write focused, runnable unit tests that exercise the
            behaviour introduced or modified by this pull request — happy paths,
            edge cases and error handling — and that would fail if the change
            regressed.

            Hard requirements:
              * Test behaviour, not implementation details. Assert on observable
                outputs and side effects, not private fields.
              * Every test must be runnable as written — no placeholders, no
                TODOs, no stubbed assertions.
              * Match the existing project conventions (test framework,
                assertion library, naming) visible in the changed files.
              * Keep the suite small and high-signal: a handful of meaningful
                tests beats dozens of trivial ones.""";

    /**
     * Non-editable protocol suffix. Pins the framework key, the allowed test
     * directories and the {@code unit-test-write} tool contract the workflow
     * relies on.
     */
    public static final String AUTHOR_PROTOCOL_SUFFIX = """
            Target build/test toolchain: {framework}.

            Write every test file by calling the `unit-test-write` tool, once
            per file:
              * The `path` argument must be a checkout-relative path under one
                of the project's conventional test source directories
                ({testDirs}). You may only write test files — production code
                is off-limits and any write outside these directories is
                rejected.
              * The `content` argument must be the complete UTF-8 source of the
                test file — no placeholders, no TODOs.
              * Reuse the exact package / import / framework conventions you see
                in the changed files so the project's own test runner compiles
                and executes your tests without extra configuration.

            After every test file has been written, reply with the single line
            `DONE` and stop.""";

    /**
     * Resolves the author system prompt by concatenating the editable section
     * (operator-edited if present, otherwise the built-in default) with the
     * non-editable protocol suffix rendered for the given framework.
     */
    public static String authorSystemPromptOrDefault(SystemPrompt systemPrompt, UnitTestFramework framework) {
        String editable = pick(systemPrompt == null ? null : systemPrompt.getUnitTestAuthorSystemPrompt(),
                DEFAULT_AUTHOR_EDITABLE);
        return editable + SECTION_SEPARATOR + render(AUTHOR_PROTOCOL_SUFFIX, framework);
    }

    private static String pick(String stored, String fallback) {
        return (stored == null || stored.isBlank()) ? fallback : stored;
    }

    private static String render(String template, UnitTestFramework framework) {
        String key = framework == null ? "" : framework.key();
        String dirs = framework == null ? "" : String.join(", ", framework.allowedTestPrefixes());
        return template.replace(FRAMEWORK_PLACEHOLDER, key).replace(TEST_DIRS_PLACEHOLDER, dirs);
    }
}

