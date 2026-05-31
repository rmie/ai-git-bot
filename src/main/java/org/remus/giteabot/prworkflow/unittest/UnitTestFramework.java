package org.remus.giteabot.prworkflow.unittest;

import java.util.List;

/**
 * Build/test toolchains the {@code unit-test-author} workflow knows how to
 * run. The string {@link #key()} is persisted in
 * {@code unit_test_suites.framework}; renaming a value is a breaking schema
 * change and must come with a Flyway migration.
 *
 * <p>Unlike the E2E workflow, the unit-test workflow operates on a full
 * checkout of the PR head branch and invokes the project's <em>own</em> test
 * runner, so each value also carries the default run command and the
 * conventional source root new tests are allowed to be written into.</p>
 */
public enum UnitTestFramework {

    /** Apache Maven (`mvn test`); JUnit tests under {@code src/test/java}. */
    MAVEN("maven", List.of("src/test/"), List.of(), List.of(), List.of("mvn", "-q", "-B", "test")),

    /** Gradle (`gradle test`); JUnit/Kotlin tests under {@code src/test}. */
    GRADLE("gradle", List.of("src/test/"), List.of(), List.of(), List.of("gradle", "test", "--quiet")),

    /**
     * Node.js (`npm test`). Tests live under a top-level {@code test/},
     * {@code tests/} or {@code __tests__/} directory, in a {@code __tests__/}
     * directory nested anywhere (the common {@code src/&#42;&#42;/__tests__/}
     * convention), or co-located next to source as {@code *.test.*} /
     * {@code *.spec.*} files. Bare {@code src/} is intentionally <em>not</em>
     * allowed — that would let the agent overwrite production modules.
     */
    NPM("npm",
            List.of("test/", "tests/", "__tests__/"),
            List.of("__tests__"),
            List.of(".test.js", ".test.jsx", ".test.ts", ".test.tsx", ".test.mjs", ".test.cjs",
                    ".spec.js", ".spec.jsx", ".spec.ts", ".spec.tsx", ".spec.mjs", ".spec.cjs"),
            List.of("npm", "test", "--silent")),

    /** Python (`python3 -m pytest`); tests under {@code tests/} or {@code test/}. */
    PYTEST("pytest", List.of("tests/", "test/"), List.of(), List.of(), List.of("python3", "-m", "pytest", "-q")),

    /** Go (`go test ./...`); {@code *_test.go} next to the package under test. */
    GO("go", List.of(), List.of(), List.of("_test.go"), List.of("go", "test", "./...", "-count=1")),

    /**
     * Rust (`cargo test`). Only the dedicated {@code tests/} integration-test
     * directory is writable. Cargo's inline {@code #[cfg(test)]} unit tests
     * live <em>inside</em> production {@code src/} modules, so allowing
     * {@code src/} would break the "production code is never touched"
     * guarantee — those are deliberately out of scope.
     */
    CARGO("cargo", List.of("tests/"), List.of(), List.of(), List.of("cargo", "test", "--quiet")),

    /** .NET (`dotnet test`); xUnit/NUnit/MSTest projects under {@code tests/} or {@code test/} (matched case-insensitively, so {@code Tests/} works too). */
    DOTNET("dotnet", List.of("tests/", "test/"), List.of(), List.of(), List.of("dotnet", "test", "--nologo", "--verbosity", "quiet")),

    /** Ruby (`bundle exec rake test`); minitest under {@code test/} or RSpec under {@code spec/}. */
    BUNDLE("bundle", List.of("test/", "spec/"), List.of(), List.of(), List.of("bundle", "exec", "rake", "test")),

    /** Generic make-driven suite (`make test`); tests under {@code tests/} or {@code test/}. */
    MAKE("make", List.of("tests/", "test/"), List.of(), List.of(), List.of("make", "test")),

    /**
     * C projects compiled with gcc. There is no shell-free one-shot
     * "compile + link + run" command, so the suite is driven through the
     * project's Makefile ({@code make test}) — the conventional C test entry
     * point. The distinct framework lets auto-detection and the author agent
     * treat it as C (write {@code .c} tests).
     */
    GCC("gcc", List.of("tests/", "test/"), List.of(), List.of(), List.of("make", "test")),

    /** C++ projects compiled with g++; driven through the Makefile like {@link #GCC}, but the agent writes {@code .cpp} tests. */
    GPP("g++", List.of("tests/", "test/"), List.of(), List.of(), List.of("make", "test"));

    private final String key;
    private final List<String> allowedTestPrefixes;
    private final List<String> allowedTestPathSegments;
    private final List<String> allowedTestFileSuffixes;
    private final List<String> defaultRunCommand;

    UnitTestFramework(String key,
                      List<String> allowedTestPrefixes,
                      List<String> allowedTestPathSegments,
                      List<String> allowedTestFileSuffixes,
                      List<String> defaultRunCommand) {
        this.key = key;
        this.allowedTestPrefixes = allowedTestPrefixes;
        this.allowedTestPathSegments = allowedTestPathSegments;
        this.allowedTestFileSuffixes = allowedTestFileSuffixes;
        this.defaultRunCommand = defaultRunCommand;
    }

    public String key() {
        return key;
    }

    /**
     * Workspace-relative directory prefixes the author agent is allowed to
     * write new test files into (matched case-insensitively). These are
     * dedicated test roots only — never a production source root such as
     * {@code src/} — so the write guard can never let the agent overwrite
     * application code.
     */
    public List<String> allowedTestPrefixes() {
        return allowedTestPrefixes;
    }

    /**
     * Path segments that mark a test directory wherever they appear in the
     * tree (e.g. {@code __tests__} for the {@code src/&#42;&#42;/__tests__/}
     * convention). A file is accepted when one of these is a full path
     * segment of its location. Empty for frameworks that keep all tests under
     * a fixed root.
     */
    public List<String> allowedTestPathSegments() {
        return allowedTestPathSegments;
    }

    /**
     * Filename suffixes that identify a file as a test regardless of its
     * directory (e.g. {@code _test.go} for Go, {@code .test.js} /
     * {@code .spec.ts} for co-located JavaScript/TypeScript tests). These
     * always create new test files and therefore never modify production
     * code.
     */
    public List<String> allowedTestFileSuffixes() {
        return allowedTestFileSuffixes;
    }

    public List<String> defaultRunCommand() {
        return defaultRunCommand;
    }

    public static UnitTestFramework fromKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Framework key must not be null");
        }
        for (UnitTestFramework f : values()) {
            if (f.key.equalsIgnoreCase(value) || f.name().equalsIgnoreCase(value)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unknown unit-test framework: " + value);
    }
}
