package org.remus.giteabot.prworkflow.unittest;

import java.util.Locale;

/**
 * Single source of truth for deciding whether a workspace-relative path is a
 * legitimate <em>test</em> location for a given {@link UnitTestFramework}.
 *
 * <p>Both the write-time guard ({@code UnitTestToolExecutor}) and the
 * pre-commit guard ({@code UnitTestService}) delegate here so the two layers
 * can never drift apart. The rule deliberately rejects bare production source
 * roots (e.g. {@code src/index.js}, {@code src/lib.rs}) — a file is accepted
 * only when it lives under a dedicated test root, in a test directory segment
 * (e.g. {@code __tests__}), or carries a test filename convention
 * (e.g. {@code *_test.go}, {@code *.test.js}).</p>
 */
public final class UnitTestPathGuard {

    private UnitTestPathGuard() {
    }

    /**
     * @param framework the toolchain whose conventions apply
     * @param path      a workspace-relative path (forward or back slashes)
     * @return {@code true} when the path is an allowed test location
     */
    public static boolean isAllowedTestPath(UnitTestFramework framework, String path) {
        if (framework == null || path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        // Strip a leading "./" so "./tests/foo" matches the "tests/" prefix.
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isBlank()) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);

        // 1. Dedicated test-root prefixes (case-insensitive: test-dir naming is
        //    convention-driven and varies by platform, e.g. Tests/ vs tests/).
        for (String prefix : framework.allowedTestPrefixes()) {
            if (!prefix.isEmpty() && lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        // 2. Test directory segments that may appear anywhere (e.g. src/**/__tests__/).
        for (String segment : framework.allowedTestPathSegments()) {
            String needle = segment.toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) {
                continue;
            }
            if (lower.startsWith(needle + "/") || lower.contains("/" + needle + "/")) {
                return true;
            }
        }
        // 3. Filename conventions allowed regardless of directory (e.g. *_test.go).
        for (String suffix : framework.allowedTestFileSuffixes()) {
            if (!suffix.isEmpty() && lower.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}

