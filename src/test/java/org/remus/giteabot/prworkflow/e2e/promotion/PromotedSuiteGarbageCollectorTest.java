package org.remus.giteabot.prworkflow.e2e.promotion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.remus.giteabot.prworkflow.PrWorkflowRun;
import org.remus.giteabot.prworkflow.PrWorkflowRunRepository;
import org.remus.giteabot.prworkflow.e2e.PrTestSuite;
import org.remus.giteabot.prworkflow.e2e.PrTestSuiteRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotedSuiteGarbageCollectorTest {

    private PrWorkflowRunRepository runRepo;
    private PrTestSuiteRepository suiteRepo;
    private PromotedSuiteGarbageCollector gc;
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        runRepo = mock(PrWorkflowRunRepository.class);
        suiteRepo = mock(PrTestSuiteRepository.class);
        fixedNow = Instant.parse("2026-05-20T12:00:00Z");
        gc = new PromotedSuiteGarbageCollector(runRepo, suiteRepo, Duration.ofDays(30));
        Supplier<Instant> clock = () -> fixedNow;
        gc.setNowSupplierForTest(clock);
    }

    @Test
    void noStaleRuns_returnsZeroAndTouchesNothing() {
        when(runRepo.findByFollowUpPrNumberIsNotNullAndFinishedAtBefore(any()))
                .thenReturn(List.of());

        assertThat(gc.collectOnce()).isZero();
        verify(suiteRepo, never()).deleteAll(any());
    }

    @Test
    void usesRetentionWindowAsCutoff() {
        when(runRepo.findByFollowUpPrNumberIsNotNullAndFinishedAtBefore(any()))
                .thenReturn(List.of());

        gc.collectOnce();

        Instant expectedCutoff = fixedNow.minus(Duration.ofDays(30));
        verify(runRepo).findByFollowUpPrNumberIsNotNullAndFinishedAtBefore(expectedCutoff);
    }

    @Test
    void deletesAllSuitesForEachStaleRun() {
        PrWorkflowRun run1 = run(101L, 42L);
        PrWorkflowRun run2 = run(102L, 43L);
        PrTestSuite s1a = suite(); PrTestSuite s1b = suite();
        PrTestSuite s2 = suite();
        when(runRepo.findByFollowUpPrNumberIsNotNullAndFinishedAtBefore(any()))
                .thenReturn(List.of(run1, run2));
        when(suiteRepo.findByRunId(101L)).thenReturn(List.of(s1a, s1b));
        when(suiteRepo.findByRunId(102L)).thenReturn(List.of(s2));

        int deleted = gc.collectOnce();

        assertThat(deleted).isEqualTo(3);
        verify(suiteRepo).deleteAll(List.of(s1a, s1b));
        verify(suiteRepo).deleteAll(List.of(s2));
    }

    @Test
    void skipsRunsWithNoSuites() {
        PrWorkflowRun run = run(200L, 99L);
        when(runRepo.findByFollowUpPrNumberIsNotNullAndFinishedAtBefore(any()))
                .thenReturn(List.of(run));
        when(suiteRepo.findByRunId(200L)).thenReturn(List.of());

        assertThat(gc.collectOnce()).isZero();
        verify(suiteRepo, never()).deleteAll(any());
    }

    @Test
    void neverDeletesTheWorkflowRunItself() {
        PrWorkflowRun run = run(300L, 7L);
        when(runRepo.findByFollowUpPrNumberIsNotNullAndFinishedAtBefore(any()))
                .thenReturn(List.of(run));
        when(suiteRepo.findByRunId(300L)).thenReturn(List.of(suite()));

        gc.collectOnce();

        // The promoted-PR link (PrWorkflowRun.followUpPrNumber) must
        // remain so the dashboard can still surface "promoted as PR #N".
        verify(runRepo, never()).delete(any());
        verify(runRepo, never()).deleteAll(any());
        verify(suiteRepo, times(1)).deleteAll(any());
    }

    private PrWorkflowRun run(long id, long followUpPr) {
        PrWorkflowRun r = new PrWorkflowRun();
        r.setId(id);
        r.setFollowUpPrNumber(followUpPr);
        r.setFinishedAt(fixedNow.minus(Duration.ofDays(60)));
        return r;
    }

    private PrTestSuite suite() {
        return new PrTestSuite();
    }
}

