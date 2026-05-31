package org.remus.giteabot.prworkflow.unittest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects the build/test toolchain of a checked-out repository by probing for
 * the marker files each ecosystem ships. Used when the operator leaves the
 * {@code framework} workflow parameter on {@code auto}.
 *
 * <p>Probing order is deliberate: a polyglot repo with both a {@code pom.xml}
 * and a {@code package.json} is far more likely to want its JVM tests run, so
 * Maven/Gradle win over npm; the more ambiguous ecosystems (Go, Rust, Ruby,
 * Python, .NET) are checked next, and a bare {@code Makefile} (C / C++ /
 * generic) is the last resort.</p>
 */
@Slf4j
@Component
public class FrameworkDetector {

    /**
     * Returns the detected framework, or {@link Optional#empty()} when no known
     * marker file is present (the workflow then skips with a clear message).
     */
    public Optional<UnitTestFramework> detect(Path checkout) {
        if (checkout == null || !Files.isDirectory(checkout)) {
            return Optional.empty();
        }
        if (exists(checkout, "pom.xml")) {
            return Optional.of(UnitTestFramework.MAVEN);
        }
        if (exists(checkout, "build.gradle") || exists(checkout, "build.gradle.kts")
                || exists(checkout, "settings.gradle") || exists(checkout, "settings.gradle.kts")) {
            return Optional.of(UnitTestFramework.GRADLE);
        }
        if (exists(checkout, "package.json")) {
            return Optional.of(UnitTestFramework.NPM);
        }
        if (exists(checkout, "go.mod")) {
            return Optional.of(UnitTestFramework.GO);
        }
        if (exists(checkout, "Cargo.toml")) {
            return Optional.of(UnitTestFramework.CARGO);
        }
        if (exists(checkout, "Gemfile")) {
            return Optional.of(UnitTestFramework.BUNDLE);
        }
        if (exists(checkout, "pytest.ini") || exists(checkout, "pyproject.toml")
                || exists(checkout, "setup.py") || exists(checkout, "setup.cfg")
                || exists(checkout, "tox.ini")) {
            return Optional.of(UnitTestFramework.PYTEST);
        }
        if (hasGlob(checkout, ".sln") || hasGlob(checkout, ".csproj") || hasGlob(checkout, ".fsproj")) {
            return Optional.of(UnitTestFramework.DOTNET);
        }
        // C / C++ and generic make-driven projects: distinguish by source extension,
        // falling back to a generic make suite. All three are driven via `make test`.
        if (exists(checkout, "Makefile") || exists(checkout, "makefile") || exists(checkout, "GNUmakefile")) {
            if (hasGlob(checkout, ".cpp") || hasGlob(checkout, ".cc") || hasGlob(checkout, ".cxx")) {
                return Optional.of(UnitTestFramework.GPP);
            }
            if (hasGlob(checkout, ".c")) {
                return Optional.of(UnitTestFramework.GCC);
            }
            return Optional.of(UnitTestFramework.MAKE);
        }
        log.info("FrameworkDetector: no known marker file found in {}", checkout);
        return Optional.empty();
    }

    private static boolean exists(Path checkout, String fileName) {
        return Files.isRegularFile(checkout.resolve(fileName));
    }

    /** Shallow check for any top-level file whose name ends with {@code suffix}. */
    private static boolean hasGlob(Path checkout, String suffix) {
        try (java.util.stream.Stream<Path> entries = Files.list(checkout)) {
            return entries.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().endsWith(suffix));
        } catch (java.io.IOException e) {
            return false;
        }
    }
}



