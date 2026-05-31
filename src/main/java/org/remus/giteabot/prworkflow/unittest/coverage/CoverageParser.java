package org.remus.giteabot.prworkflow.unittest.coverage;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.prworkflow.unittest.UnitTestFramework;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort line-coverage extraction from the artefacts the project's own
 * test runner leaves behind. Every parser is defensive — a missing or
 * unparseable report yields {@link CoverageResult#unknown()} and never throws,
 * because coverage is a "nice to have" decoration on the PR comment, not a
 * gate.
 *
 * <p>Supported sources:</p>
 * <ul>
 *   <li>Maven / Gradle — JaCoCo {@code jacoco.xml} (LINE counter)</li>
 *   <li>pytest / .NET   — Cobertura {@code coverage.xml} ({@code line-rate} attribute)</li>
 *   <li>npm / make / gcc / g++ — {@code coverage/lcov.info} (LF/LH records)</li>
 *   <li>go     — {@code coverage: NN.N% of statements} on the runner stdout</li>
 *   <li>cargo / bundle — no standard report; reported as unknown</li>
 * </ul>
 */
@Slf4j
@Component
public class CoverageParser {

    private static final Pattern JACOCO_LINE_COUNTER = Pattern.compile(
            "<counter\\s+type=\"LINE\"\\s+missed=\"(\\d+)\"\\s+covered=\"(\\d+)\"\\s*/>");
    private static final Pattern COBERTURA_LINE_RATE = Pattern.compile(
            "line-rate=\"([0-9.]+)\"");
    private static final Pattern GO_COVER = Pattern.compile(
            "coverage:\\s*([0-9.]+)%\\s+of\\s+statements");

    /**
     * Resolves a coverage snapshot for the given framework after a run.
     *
     * @param workspace the repository checkout the runner executed in
     * @param framework the framework whose report layout to look for
     * @param stdout    the runner's combined stdout/stderr (used by Go)
     */
    public CoverageResult parse(Path workspace, UnitTestFramework framework, String stdout) {
        try {
            return switch (framework) {
                case MAVEN, GRADLE -> parseJacoco(workspace);
                case PYTEST, DOTNET -> parseCobertura(workspace);
                case NPM, GCC, GPP, MAKE -> parseLcov(workspace);
                case GO -> parseGoStdout(stdout);
                // Cargo / Bundle have no single conventional XML/lcov report we
                // can read without extra tooling — best-effort means "unknown".
                case CARGO, BUNDLE -> CoverageResult.unknown();
            };
        } catch (RuntimeException e) {
            log.debug("Coverage parsing failed for {}: {}", framework, e.getMessage());
            return CoverageResult.unknown();
        }
    }

    private CoverageResult parseJacoco(Path workspace) {
        // Maven writes to target/site/jacoco; Gradle to build/reports/jacoco/test.
        for (String candidate : List.of(
                "target/site/jacoco/jacoco.xml",
                "build/reports/jacoco/test/jacocoTestReport.xml")) {
            Path report = workspace.resolve(candidate);
            if (!Files.isRegularFile(report)) {
                continue;
            }
            String xml = readString(report);
            if (xml == null) {
                continue;
            }
            // The report-level LINE counter is the last one in the file.
            Matcher m = JACOCO_LINE_COUNTER.matcher(xml);
            long missed = 0;
            long covered = 0;
            boolean found = false;
            while (m.find()) {
                missed = Long.parseLong(m.group(1));
                covered = Long.parseLong(m.group(2));
                found = true;
            }
            if (found) {
                return CoverageResult.of(covered, covered + missed);
            }
        }
        return CoverageResult.unknown();
    }

    private CoverageResult parseCobertura(Path workspace) {
        for (String candidate : List.of("coverage.xml", "cobertura.xml", "coverage.cobertura.xml")) {
            Path report = workspace.resolve(candidate);
            if (!Files.isRegularFile(report)) {
                continue;
            }
            String xml = readString(report);
            if (xml == null) {
                continue;
            }
            Matcher m = COBERTURA_LINE_RATE.matcher(xml);
            if (m.find()) {
                double rate = Double.parseDouble(m.group(1));
                // Cobertura line-rate is a 0..1 fraction; scale to a 0..10000 integer
                // basis so we keep two decimals of precision without a float field.
                long total = 10_000;
                long covered = Math.round(rate * total);
                return CoverageResult.of(covered, total);
            }
        }
        return CoverageResult.unknown();
    }

    private CoverageResult parseLcov(Path workspace) {
        for (String candidate : List.of("coverage/lcov.info", "lcov.info")) {
            Path report = workspace.resolve(candidate);
            if (!Files.isRegularFile(report)) {
                continue;
            }
            String lcov = readString(report);
            if (lcov == null) {
                continue;
            }
            long found = 0;
            long hit = 0;
            for (String line : lcov.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("LF:")) {
                    found += parseLongSafe(trimmed.substring(3));
                } else if (trimmed.startsWith("LH:")) {
                    hit += parseLongSafe(trimmed.substring(3));
                }
            }
            if (found > 0) {
                return CoverageResult.of(hit, found);
            }
        }
        return CoverageResult.unknown();
    }

    private CoverageResult parseGoStdout(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return CoverageResult.unknown();
        }
        Matcher m = GO_COVER.matcher(stdout);
        double percent = -1;
        while (m.find()) {
            percent = Double.parseDouble(m.group(1));
        }
        if (percent < 0) {
            return CoverageResult.unknown();
        }
        long total = 10_000;
        long covered = Math.round((percent / 100.0) * total);
        return CoverageResult.of(covered, total);
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLongSafe(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}



