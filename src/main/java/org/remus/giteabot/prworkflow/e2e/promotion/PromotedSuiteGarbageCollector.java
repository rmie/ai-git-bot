package org.remus.giteabot.prworkflow.e2e.promotion;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.prworkflow.PrWorkflowRun;
import org.remus.giteabot.prworkflow.PrWorkflowRunRepository;
import org.remus.giteabot.prworkflow.e2e.PrTestSuite;
import org.remus.giteabot.prworkflow.e2e.PrTestSuiteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * M7 — nightly garbage collector for promoted suites.
 *
 * <p>The M7 spec keeps generated {@link PrTestSuite} rows alive past PR
 * close when the lifecycle mode is {@code OFFER_AS_PR},
 * {@code PROMOTE_ON_MERGE} or {@code COMMIT_TO_PR} so the operator dashboard
 * can correlate the parent {@link PrWorkflowRun} with the resulting
 * follow-up PR. Once the follow-up PR has had a reasonable amount of time
 * to be reviewed/merged/closed there is no further value in the in-DB copy
 * of the test source — the canonical source has graduated into the
 * repository itself.</p>
 *
 * <p>This collector runs on a fixed cron schedule (default: nightly at
 * 03:17 server time) and deletes every {@link PrTestSuite} whose owning
 * {@link PrWorkflowRun} has both a non-null {@code followUpPrNumber} <em>and</em>
 * a {@code finishedAt} older than the configured retention window
 * ({@code prworkflow.e2e.promotion.retention} — default 30 days). Cases
 * are removed via the existing {@code orphanRemoval=true} cascade on
 * {@code PrTestSuite.cases}. The {@code PrWorkflowRun} row itself is kept
 * so the dashboard can still show "promoted as PR #N" links.</p>
 *
 * <p>Why a time-based GC and not a PR-state probe: surveying the
 * follow-up PR's open/closed state across four providers would add a
 * cross-cutting API and rate-limit pressure for very little benefit —
 * the source of truth is already in git after the promotion succeeded,
 * so a generous timeout is enough.</p>
 */
@Slf4j
@Component
public class PromotedSuiteGarbageCollector {

    private final PrWorkflowRunRepository runRepository;
    private final PrTestSuiteRepository suiteRepository;
    private final Duration retention;
    /** Injectable "now" source — overridden by tests to avoid wall-clock sleeps. */
    private Supplier<Instant> nowSupplier = Instant::now;

    public PromotedSuiteGarbageCollector(
            PrWorkflowRunRepository runRepository,
            PrTestSuiteRepository suiteRepository,
            @Value("${prworkflow.e2e.promotion.retention:P30D}") Duration retention) {
        this.runRepository = runRepository;
        this.suiteRepository = suiteRepository;
        this.retention = retention;
    }

    /** Test seam — replaces the wall-clock with a deterministic supplier. */
    void setNowSupplierForTest(Supplier<Instant> nowSupplier) {
        this.nowSupplier = nowSupplier;
    }

    /**
     * Cron-fired entry point. Default schedule: nightly at 03:17 server
     * time — picked outside the typical webhook burst window. Tunable via
     * {@code prworkflow.e2e.promotion.gc-cron}.
     */
    @Scheduled(cron = "${prworkflow.e2e.promotion.gc-cron:0 17 3 * * *}")
    public void runGarbageCollection() {
        int deleted = collectOnce();
        if (deleted > 0) {
            log.info("PromotedSuiteGarbageCollector: deleted {} stale suite row(s) (retention={})",
                    deleted, retention);
        } else {
            log.debug("PromotedSuiteGarbageCollector: no stale suites past retention={}",
                    retention);
        }
    }

    /**
     * Single GC pass; returns the number of suites deleted. Package-private
     * so {@code PromotedSuiteGarbageCollectorTest} can drive it deterministically
     * without waiting for the cron.
     */
    int collectOnce() {
        Instant cutoff = nowSupplier.get().minus(retention);
        List<PrWorkflowRun> stale = runRepository
                .findByFollowUpPrNumberIsNotNullAndFinishedAtBefore(cutoff);
        if (stale.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (PrWorkflowRun run : stale) {
            List<PrTestSuite> suites = suiteRepository.findByRunId(run.getId());
            if (suites.isEmpty()) continue;
            suiteRepository.deleteAll(suites);
            deleted += suites.size();
            log.debug("PromotedSuiteGarbageCollector: removed {} suite(s) for run id={} (followUpPr=#{})",
                    suites.size(), run.getId(), run.getFollowUpPrNumber());
        }
        return deleted;
    }
}




