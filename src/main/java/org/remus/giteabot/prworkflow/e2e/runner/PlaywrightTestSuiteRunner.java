package org.remus.giteabot.prworkflow.e2e.runner;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.admin.AiClientFactory;
import org.remus.giteabot.admin.Bot;
import org.remus.giteabot.admin.GiteaClientFactory;
import org.remus.giteabot.ai.AiClient;
import org.remus.giteabot.gitea.model.WebhookPayload;
import org.remus.giteabot.prworkflow.PrWorkflowContext;
import org.remus.giteabot.prworkflow.e2e.E2eTestFramework;
import org.remus.giteabot.prworkflow.e2e.PrTestCase;
import org.remus.giteabot.prworkflow.e2e.PrTestCaseRepository;
import org.remus.giteabot.prworkflow.e2e.PrTestCaseStatus;
import org.remus.giteabot.prworkflow.e2e.PrTestSuite;
import org.remus.giteabot.prworkflow.e2e.agents.TestAuthorAgent;
import org.remus.giteabot.prworkflow.e2e.agents.TestPlan;
import org.remus.giteabot.prworkflow.e2e.agents.TestPlanParser;
import org.remus.giteabot.prworkflow.e2e.agents.TestPlannerAgent;
import org.remus.giteabot.prworkflow.e2e.agents.TestRunnerAgent;
import org.remus.giteabot.prworkflow.e2e.tools.PrWorkflowToolContext;
import org.remus.giteabot.repository.RepositoryApiClient;
import org.remus.giteabot.systemsettings.SystemPrompt;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * M4 wave 2 LLM-driven {@link TestSuiteRunner} for
 * {@link E2eTestFramework#PLAYWRIGHT}. Orchestrates the three E2E agents:
 *
 * <ol>
 *     <li>{@link TestPlannerAgent} — turns the PR diff into a {@link TestPlan}.</li>
 *     <li>{@link TestAuthorAgent} — materialises every journey via {@code pr-test-write}.</li>
 *     <li>{@link TestRunnerAgent} — executes the suite via {@code pr-test-run}
 *         and (optionally) attaches artefacts via {@code attach-artifact}.</li>
 * </ol>
 *
 * <p>Per-case statuses are written back into the database by the tool
 * executor; the rollup here just re-reads {@link PrTestCase} rows after the
 * runner agent returns and maps them onto a {@link TestSuiteOutcome}. The
 * runner intentionally does not throw — every failure path becomes a
 * structured {@link TestSuiteOutcome} so {@code E2ETestWorkflow} can post a
 * coherent PR comment.</p>
 */
@Slf4j
@Component
public class PlaywrightTestSuiteRunner implements TestSuiteRunner {

    private final AiClientFactory aiClientFactory;
    private final GiteaClientFactory giteaClientFactory;
    private final TestPlannerAgent plannerAgent;
    private final TestAuthorAgent authorAgent;
    private final TestRunnerAgent runnerAgent;
    private final PrTestCaseRepository caseRepository;

    public PlaywrightTestSuiteRunner(AiClientFactory aiClientFactory,
                                     GiteaClientFactory giteaClientFactory,
                                     TestPlannerAgent plannerAgent,
                                     TestAuthorAgent authorAgent,
                                     TestRunnerAgent runnerAgent,
                                     PrTestCaseRepository caseRepository) {
        this.aiClientFactory = aiClientFactory;
        this.giteaClientFactory = giteaClientFactory;
        this.plannerAgent = plannerAgent;
        this.authorAgent = authorAgent;
        this.runnerAgent = runnerAgent;
        this.caseRepository = caseRepository;
    }

    @Override
    public E2eTestFramework framework() {
        return E2eTestFramework.PLAYWRIGHT;
    }

    @Override
    public TestSuiteOutcome run(TestSuiteRequest request) {
        Bot bot = request.bot();
        if (bot == null || bot.getAiIntegration() == null) {
            return TestSuiteOutcome.skipped(
                    "PlaywrightTestSuiteRunner: bot has no AI integration configured");
        }
        AiClient aiClient;
        RepositoryApiClient apiClient;
        try {
            aiClient = aiClientFactory.getClient(bot.getAiIntegration());
            apiClient = giteaClientFactory.getApiClient(bot.getGitIntegration());
        } catch (RuntimeException e) {
            log.warn("PlaywrightTestSuiteRunner: client factory threw {}: {}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return TestSuiteOutcome.error(
                    "Could not resolve AI/Repository client for bot '" + bot.getName()
                            + "': " + e.getMessage());
        }

        OwnerRepoPr addr = OwnerRepoPr.from(request.payload());

        // ── rerun-only path: restore previously generated test files, skip Planner+Author ──
        if (request.isRerunOnly()) {
            return runExistingTests(request, aiClient, apiClient, addr);
        }

        // ── full regenerate path ──
        String diff = "";
        try {
            if (addr.owner() != null && addr.repo() != null && addr.prNumber() != null) {
                diff = apiClient.getPullRequestDiff(addr.owner(), addr.repo(), addr.prNumber());
            }
        } catch (RuntimeException e) {
            log.warn("PlaywrightTestSuiteRunner: could not fetch PR diff: {}", e.getMessage());
            // Continue with empty diff — planner will simply produce no journeys.
        }

        TestPlannerAgent.PlannerInput plannerInput = new TestPlannerAgent.PlannerInput(
                request.payload().getPullRequest() == null ? "" :
                        valueOrEmpty(request.payload().getPullRequest().getTitle()),
                request.payload().getPullRequest() == null ? "" :
                        valueOrEmpty(request.payload().getPullRequest().getBody()),
                diff == null ? "" : diff,
                request.context() == null
                        ? ""
                        : valueOrEmpty(request.context().hint(PrWorkflowContext.HINT_E2E_FEEDBACK)));

        SystemPrompt systemPrompt = bot.getSystemPrompt();

        TestPlan plan = plannerAgent.plan(aiClient, request.framework(), plannerInput, systemPrompt).orElse(null);
        if (plan == null || plan.isEmpty()) {
            return TestSuiteOutcome.skipped(
                    "TestPlannerAgent returned no journeys — nothing to author or run");
        }
        // Cost guard: hard-cap journeys before authoring so we cannot exceed maxTestCases.
        if (plan.journeys().size() > request.maxTestCases()) {
            plan = TestPlanParser.capJourneys(plan, request.maxTestCases());
        }

        PrWorkflowToolContext toolContext = new PrWorkflowToolContext(
                request.suite(), request.workspace(), request.framework(),
                request.previewUrl(),
                addr.owner(), addr.repo(), addr.prNumber(),
                apiClient);

        TestAuthorAgent.Result authorResult = authorAgent.write(aiClient, toolContext, plan, systemPrompt);
        if (!authorResult.wroteAnything()) {
            return TestSuiteOutcome.error(
                    "TestAuthorAgent wrote zero files (budgetExhausted="
                            + authorResult.budgetExhausted() + ")");
        }

        int effectiveRetries = effectiveRetries(plan, request.maxRetries());
        TestRunnerAgent.Result runResult = runnerAgent.execute(aiClient, toolContext, plan, effectiveRetries, systemPrompt);

        // Aggregate per-case outcomes from the database — those are the source of truth.
        List<PrTestCase> cases = caseRepository.findBySuiteOrderByIdAsc(request.suite());
        int attempted = 0;
        int failed = 0;
        for (PrTestCase pc : cases) {
            if (pc.getLastStatus() == null || pc.getLastStatus() == PrTestCaseStatus.PENDING) {
                continue;
            }
            attempted++;
            if (pc.getLastStatus() == PrTestCaseStatus.FAILED
                    || pc.getLastStatus() == PrTestCaseStatus.ERROR) {
                failed++;
            }
        }

        if (attempted == 0) {
            return TestSuiteOutcome.error(
                    "TestRunnerAgent finished but no PrTestCase was executed (prTestRunInvocations="
                            + runResult.prTestRunInvocations() + ", budgetExhausted="
                            + runResult.budgetExhausted() + ")");
        }
        if (failed == 0) {
            String summary = attempted + "/" + cases.size() + " passed";
            if (runResult.attachedArtifacts() > 0) {
                summary += " · " + runResult.attachedArtifacts() + " artefact(s) attached";
            }
            return TestSuiteOutcome.passed(summary, attempted);
        }
        return TestSuiteOutcome.failed(
                failed + "/" + attempted + " failed", attempted, failed);
    }

    /**
     * Rerun-only path: restores all test files from the {@link PrTestSuite#getCases()
     * cases} of {@code request.previousSuite()} into the new workspace, skips
     * {@link TestPlannerAgent} and {@link TestAuthorAgent}, and directly delegates
     * to {@link TestRunnerAgent}. A minimal {@link TestPlan} reconstructed from the
     * stored cases is used solely to drive the runner agent's context.
     */
    private TestSuiteOutcome runExistingTests(TestSuiteRequest request,
                                              AiClient aiClient,
                                              RepositoryApiClient apiClient,
                                              OwnerRepoPr addr) {
        PrTestSuite previousSuite = request.previousSuite();
        List<PrTestCase> previousCases = caseRepository.findBySuiteOrderByIdAsc(previousSuite);
        if (previousCases.isEmpty()) {
            return TestSuiteOutcome.skipped(
                    "rerun-only: previous suite #" + previousSuite.getId()
                            + " has no persisted test cases — cannot rerun");
        }

        // Restore each test file from the DB into the new workspace directory.
        int restored = 0;
        for (PrTestCase pc : previousCases) {
            if (pc.getContent() == null || pc.getPath() == null) continue;
            try {
                Path dest = request.workspace().resolve(pc.getPath());
                Files.createDirectories(dest.getParent());
                Files.writeString(dest, pc.getContent());
                restored++;
            } catch (IOException e) {
                log.warn("PlaywrightTestSuiteRunner rerun: could not restore '{}': {}",
                        pc.getPath(), e.getMessage());
            }
        }
        if (restored == 0) {
            return TestSuiteOutcome.error(
                    "rerun-only: failed to restore any test files from suite #"
                            + previousSuite.getId() + " into workspace");
        }
        log.info("PlaywrightTestSuiteRunner rerun: restored {}/{} test files from suite #{}",
                restored, previousCases.size(), previousSuite.getId());

        // Reconstruct a minimal TestPlan so the runner agent has journey context.
        List<TestPlan.Journey> journeys = previousCases.stream()
                .filter(pc -> pc.getPath() != null)
                .map(pc -> new TestPlan.Journey(
                        journeyIdFromPath(pc.getPath()),
                        pc.getTitle() != null ? pc.getTitle() : pc.getPath(),
                        List.of(),
                        List.of(),
                        pc.getPath()))
                .toList();
        TestPlan plan = new TestPlan(request.framework().key(), journeys, request.maxRetries());

        PrWorkflowToolContext toolContext = new PrWorkflowToolContext(
                request.suite(), request.workspace(), request.framework(),
                request.previewUrl(),
                addr.owner(), addr.repo(), addr.prNumber(),
                apiClient);

        SystemPrompt systemPrompt = request.bot().getSystemPrompt();
        int effectiveRetries = effectiveRetries(plan, request.maxRetries());
        TestRunnerAgent.Result runResult = runnerAgent.execute(
                aiClient, toolContext, plan, effectiveRetries, systemPrompt);

        List<PrTestCase> cases = caseRepository.findBySuiteOrderByIdAsc(request.suite());
        int attempted = 0;
        int failed = 0;
        for (PrTestCase pc : cases) {
            if (pc.getLastStatus() == null || pc.getLastStatus() == PrTestCaseStatus.PENDING) {
                continue;
            }
            attempted++;
            if (pc.getLastStatus() == PrTestCaseStatus.FAILED
                    || pc.getLastStatus() == PrTestCaseStatus.ERROR) {
                failed++;
            }
        }
        if (attempted == 0) {
            return TestSuiteOutcome.error(
                    "rerun: TestRunnerAgent finished but no PrTestCase was executed (prTestRunInvocations="
                            + runResult.prTestRunInvocations() + ", budgetExhausted="
                            + runResult.budgetExhausted() + ")");
        }
        if (failed == 0) {
            String summary = attempted + "/" + cases.size() + " passed (rerun)";
            if (runResult.attachedArtifacts() > 0) {
                summary += " · " + runResult.attachedArtifacts() + " artefact(s) attached";
            }
            return TestSuiteOutcome.passed(summary, attempted);
        }
        return TestSuiteOutcome.failed(failed + "/" + attempted + " failed (rerun)", attempted, failed);
    }

    /** Derives a stable journey id from a test file path, e.g. {@code tests/login.spec.ts → login}. */
    private static String journeyIdFromPath(String path) {
        if (path == null) return "journey";
        String name = Path.of(path).getFileName().toString();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static int effectiveRetries(TestPlan plan, int workflowDefault) {        Integer fromPlan = plan.maxRetries();
        if (fromPlan == null) return workflowDefault;
        return Math.max(0, Math.min(workflowDefault, fromPlan));
    }

    private static String valueOrEmpty(String s) {
        return s == null ? "" : s;
    }

    private record OwnerRepoPr(String owner, String repo, Long prNumber) {
        static OwnerRepoPr from(WebhookPayload payload) {
            if (payload == null) return new OwnerRepoPr(null, null, null);
            String owner = payload.getRepository() == null || payload.getRepository().getOwner() == null
                    ? null : payload.getRepository().getOwner().getLogin();
            String repo  = payload.getRepository() == null ? null : payload.getRepository().getName();
            Long pr = payload.getPullRequest() == null ? null : payload.getPullRequest().getNumber();
            return new OwnerRepoPr(owner, repo, pr);
        }
    }
}



