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
    MAVEN("maven", List.of("src/test/"), List.of("mvn", "-q", "-B", "test")),

    /** Gradle (`gradle test`); JUnit/Kotlin tests under {@code src/test}. */
    GRADLE("gradle", List.of("src/test/"), List.of("gradle", "test", "--quiet")),

    /** Node.js (`npm test`); tests under {@code test/}, {@code tests/} or {@code __tests__/}. */
    NPM("npm", List.of("test/", "tests/", "__tests__/", "src/"), List.of("npm", "test", "--silent")),

    /** Python (`python3 -m pytest`); tests under {@code tests/} or {@code test/}. */
    PYTEST("pytest", List.of("tests/", "test/"), List.of("python3", "-m", "pytest", "-q")),

    /** Go (`go test ./...`); {@code *_test.go} next to the package under test. */
    GO("go", List.of(""), List.of("go", "test", "./...", "-count=1")),

    /** Rust (`cargo test`); unit tests in {@code src/} ({@code #[cfg(test)]}), integration tests in {@code tests/}. */
    CARGO("cargo", List.of("tests/", "src/"), List.of("cargo", "test", "--quiet")),

    /** .NET (`dotnet test`); xUnit/NUnit/MSTest projects under {@code tests/} or {@code test/}. */
    DOTNET("dotnet", List.of("tests/", "test/", "Tests/"), List.of("dotnet", "test", "--nologo", "--verbosity", "quiet")),

    /** Ruby (`bundle exec rake test`); minitest under {@code test/} or RSpec under {@code spec/}. */
    BUNDLE("bundle", List.of("test/", "spec/"), List.of("bundle", "exec", "rake", "test")),

    /** Generic make-driven suite (`make test`); tests under {@code tests/} or {@code test/}. */
    MAKE("make", List.of("tests/", "test/"), List.of("make", "test")),

    /**
     * C projects compiled with gcc. There is no shell-free one-shot
     * "compile + link + run" command, so the suite is driven through the
     * project's Makefile ({@code make test}) — the conventional C test entry
     * point. The distinct framework lets auto-detection and the author agent
     * treat it as C (write {@code .c} tests).
     */
    GCC("gcc", List.of("tests/", "test/"), List.of("make", "test")),

    /** C++ projects compiled with g++; driven through the Makefile like {@link #GCC}, but the agent writes {@code .cpp} tests. */
    GPP("g++", List.of("tests/", "test/"), List.of("make", "test"));

    private final String key;
    private final List<String> allowedTestPrefixes;
    private final List<String> defaultRunCommand;

    UnitTestFramework(String key, List<String> allowedTestPrefixes, List<String> defaultRunCommand) {
        this.key = key;
        this.allowedTestPrefixes = allowedTestPrefixes;
        this.defaultRunCommand = defaultRunCommand;
    }

    public String key() {
        return key;
    }

    /**
     * Workspace-relative path prefixes the author agent is allowed to write
     * new test files into. An empty-string entry means "anywhere in the
     * checkout" (used by Go, whose {@code *_test.go} files live next to the
     * code under test).
     */
    public List<String> allowedTestPrefixes() {
        return allowedTestPrefixes;
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
