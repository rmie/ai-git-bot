package org.remus.giteabot.prworkflow.unittest.coverage;

/**
 * Best-effort line-coverage snapshot for one runner invocation. Coverage is
 * never authoritative — when a project ships no coverage tooling the parser
 * returns {@link #unknown()} and the workflow simply omits the coverage line
 * from the PR comment.
 *
 * @param known        whether a coverage percentage could be determined
 * @param coveredLines covered instruction/line count (0 when unknown)
 * @param totalLines   total instruction/line count (0 when unknown)
 */
public record CoverageResult(boolean known, long coveredLines, long totalLines) {

    public static CoverageResult unknown() {
        return new CoverageResult(false, 0, 0);
    }

    public static CoverageResult of(long covered, long total) {
        if (total <= 0) {
            return unknown();
        }
        return new CoverageResult(true, Math.max(0, covered), total);
    }

    /** Coverage percentage in [0,100], or {@code -1} when unknown. */
    public double percent() {
        if (!known || totalLines <= 0) {
            return -1;
        }
        return (coveredLines * 100.0) / totalLines;
    }
}

