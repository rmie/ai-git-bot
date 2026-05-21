package org.remus.giteabot.prworkflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.remus.giteabot.admin.Bot;
import org.remus.giteabot.gitea.model.WebhookPayload;
import org.remus.giteabot.prworkflow.config.WorkflowSelectionService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrWorkflowOrchestratorTest {

    @Mock private PrWorkflowRunService runService;
    @Mock private PrWorkflowMetrics metrics;

    private final PrWorkflowRunLockManager lockManager = new PrWorkflowRunLockManager();

    private PrWorkflowOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Built explicitly in each test once the workflow set is known.
    }

    private PrWorkflowOrchestrator newOrchestrator(PrWorkflow... workflows) {
        PrWorkflowRegistry registry = new PrWorkflowRegistry(List.of(workflows));
        return new PrWorkflowOrchestrator(registry, runService, metrics, lockManager,
                org.mockito.Mockito.mock(WorkflowSelectionService.class));
    }

    private static WebhookPayload payloadFor(String owner, String repo, long prNumber) {
        WebhookPayload payload = new WebhookPayload();
        WebhookPayload.Repository repository = new WebhookPayload.Repository();
        WebhookPayload.Owner user = new WebhookPayload.Owner();
        user.setLogin(owner);
        repository.setOwner(user);
        repository.setName(repo);
        payload.setRepository(repository);
        WebhookPayload.PullRequest pr = new WebhookPayload.PullRequest();
        pr.setNumber(prNumber);
        payload.setPullRequest(pr);
        return payload;
    }

    private static Bot bot(long id, String name) {
        Bot b = new Bot();
        b.setId(id);
        b.setName(name);
        b.setUsername(name);
        return b;
    }

    private static PrWorkflowRun stubRun(long id, PrWorkflowRunStatus status) {
        PrWorkflowRun run = new PrWorkflowRun();
        run.setId(id);
        run.setStatus(status);
        return run;
    }

    @Test
    void runDelegatesToWorkflowAndPersistsSuccess() {
        AtomicReference<PrWorkflowContext> captured = new AtomicReference<>();
        PrWorkflow w = stubWorkflow("review", ctx -> {
            captured.set(ctx);
            return WorkflowResult.success("Reviewed");
        });
        orchestrator = newOrchestrator(w);
        when(runService.start(eq(42L), eq("acme"), eq("web"), eq(7L), eq("review")))
                .thenReturn(stubRun(101L, PrWorkflowRunStatus.RUNNING));
        when(runService.complete(eq(101L), eq(PrWorkflowRunStatus.SUCCESS), eq("Reviewed")))
                .thenReturn(stubRun(101L, PrWorkflowRunStatus.SUCCESS));

        PrWorkflowRun completed = orchestrator.run(bot(42L, "ai_bot"),
                payloadFor("acme", "web", 7L), "review");

        assertEquals(PrWorkflowRunStatus.SUCCESS, completed.getStatus());
        assertNotNull(captured.get());
        assertEquals(101L, captured.get().runId());
        verify(metrics).recordRun(eq("review"), eq(PrWorkflowRunStatus.SUCCESS), any());
    }

    @Test
    void runMapsSkippedToSuccessStatus() {
        PrWorkflow w = stubWorkflow("review",
                ctx -> WorkflowResult.skipped("nothing to do"));
        orchestrator = newOrchestrator(w);
        when(runService.start(anyLong(), any(), any(), anyLong(), any()))
                .thenReturn(stubRun(1L, PrWorkflowRunStatus.RUNNING));
        when(runService.complete(eq(1L), eq(PrWorkflowRunStatus.SUCCESS), eq("nothing to do")))
                .thenReturn(stubRun(1L, PrWorkflowRunStatus.SUCCESS));

        PrWorkflowRun result = orchestrator.run(bot(1L, "b"), payloadFor("o", "r", 1L), "review");

        assertEquals(PrWorkflowRunStatus.SUCCESS, result.getStatus());
    }

    @Test
    void runCapturesExceptionAsFailedAndRethrows() {
        PrWorkflow w = stubWorkflow("review", ctx -> {
            throw new IllegalStateException("boom");
        });
        orchestrator = newOrchestrator(w);
        when(runService.start(anyLong(), any(), any(), anyLong(), any()))
                .thenReturn(stubRun(7L, PrWorkflowRunStatus.RUNNING));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orchestrator.run(bot(1L, "b"), payloadFor("o", "r", 1L), "review"));
        assertEquals("boom", ex.getMessage());

        verify(runService).appendStep(eq(7L), eq("exception"), eq("ERROR"),
                ArgumentCaptor.forClass(String.class).capture());
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(runService).complete(eq(7L), eq(PrWorkflowRunStatus.FAILED), summary.capture());
        assertTrue(summary.getValue().contains("boom"));
        verify(metrics).recordRun(eq("review"), eq(PrWorkflowRunStatus.FAILED), any());
    }

    @Test
    void runThrowsOnUnknownWorkflow() {
        orchestrator = newOrchestrator(stubWorkflow("review",
                ctx -> WorkflowResult.success("ok")));

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.run(bot(1L, "b"), payloadFor("o", "r", 1L), "does-not-exist"));
    }

    @Test
    void runThrowsWhenPayloadMissesRepositoryIdentity() {
        orchestrator = newOrchestrator(stubWorkflow("review",
                ctx -> WorkflowResult.success("ok")));

        WebhookPayload payload = new WebhookPayload(); // empty
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.run(bot(1L, "b"), payload, "review"));
    }

    @Test
    void runReportsCancelledMetricWhenSupersededDuringExecution() {
        // Simulate: workflow runs to completion and returns SUCCESS, but in the
        // meantime a newer run cancelled this one — complete() is a no-op and the
        // row is still CANCELLED. The orchestrator must report CANCELLED, not SUCCESS.
        PrWorkflow w = stubWorkflow("review", ctx -> WorkflowResult.success("done"));
        orchestrator = newOrchestrator(w);
        when(runService.start(anyLong(), any(), any(), anyLong(), any()))
                .thenReturn(stubRun(55L, PrWorkflowRunStatus.RUNNING));
        when(runService.complete(eq(55L), eq(PrWorkflowRunStatus.SUCCESS), eq("done")))
                .thenReturn(stubRun(55L, PrWorkflowRunStatus.CANCELLED));

        PrWorkflowRun result = orchestrator.run(bot(1L, "b"), payloadFor("o", "r", 1L), "review");

        assertEquals(PrWorkflowRunStatus.CANCELLED, result.getStatus());
        verify(metrics).recordRun(eq("review"), eq(PrWorkflowRunStatus.CANCELLED), any());
    }

    @Test
    void runHandlesWorkflowCancelledExceptionAsCancelledNotFailed() {
        PrWorkflow w = stubWorkflow("review", ctx -> {
            throw new WorkflowCancelledException("Run 99 was superseded before: posting review");
        });
        orchestrator = newOrchestrator(w);
        when(runService.start(anyLong(), any(), any(), anyLong(), any()))
                .thenReturn(stubRun(99L, PrWorkflowRunStatus.RUNNING));
        when(runService.getById(99L)).thenReturn(stubRun(99L, PrWorkflowRunStatus.CANCELLED));

        PrWorkflowRun result = orchestrator.run(bot(1L, "b"), payloadFor("o", "r", 1L), "review");

        assertEquals(PrWorkflowRunStatus.CANCELLED, result.getStatus());
        verify(metrics).recordRun(eq("review"), eq(PrWorkflowRunStatus.CANCELLED), any());
        verify(runService).appendStep(eq(99L), eq("cancelled"), eq("INFO"),
                ArgumentCaptor.forClass(String.class).capture());
        // Must NOT have written a FAILED row or emitted a FAILED metric.
        verify(runService, never()).complete(eq(99L), eq(PrWorkflowRunStatus.FAILED), any());
        verify(metrics, never()).recordRun(eq("review"), eq(PrWorkflowRunStatus.FAILED), any());
    }

    @Test
    void runHoldsLockAcrossStartSoConcurrentDeliveriesAreSerialised() throws Exception {
        // Two webhook deliveries hit the orchestrator simultaneously for the same
        // (bot, repo, PR, workflow). The per-tuple lock must serialise their start()
        // calls — runService.start() may never overlap with itself for the same tuple.
        java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger maxInFlight = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicLong nextRunId = new java.util.concurrent.atomic.AtomicLong(1L);
        when(runService.start(anyLong(), any(), any(), anyLong(), any())).thenAnswer(inv -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(20);
            } finally {
                inFlight.decrementAndGet();
            }
            return stubRun(nextRunId.getAndIncrement(), PrWorkflowRunStatus.RUNNING);
        });
        when(runService.complete(anyLong(), any(), any()))
                .thenAnswer(inv -> stubRun(inv.getArgument(0), PrWorkflowRunStatus.SUCCESS));

        PrWorkflow w = stubWorkflow("review", ctx -> WorkflowResult.success("ok"));
        orchestrator = newOrchestrator(w);
        Bot bot = bot(1L, "b");
        WebhookPayload payload = payloadFor("o", "r", 7L);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> orchestrator.run(bot, payload, "review")));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                f.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // The critical assertion: start() never executed in parallel for the same tuple.
        assertEquals(1, maxInFlight.get(),
                "PrWorkflowRunLockManager must serialise start() for the same PR tuple");
    }

    private static PrWorkflow stubWorkflow(String key,
                                           java.util.function.Function<PrWorkflowContext, WorkflowResult> body) {
        return new PrWorkflow() {
            @Override public String key() { return key; }
            @Override public String displayName() { return key; }
            @Override public PrWorkflowCategory category() { return PrWorkflowCategory.REVIEW; }
            @Override public WorkflowResult run(PrWorkflowContext context) { return body.apply(context); }
        };
    }
}


