package org.remus.giteabot.prworkflow.unittest;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.admin.Bot;
import org.remus.giteabot.prworkflow.PrWorkflow;
import org.remus.giteabot.prworkflow.PrWorkflowCategory;
import org.remus.giteabot.prworkflow.PrWorkflowContext;
import org.remus.giteabot.prworkflow.WorkflowParamField;
import org.remus.giteabot.prworkflow.WorkflowParamsSchema;
import org.remus.giteabot.prworkflow.WorkflowResult;
import org.remus.giteabot.prworkflow.config.WorkflowSelectionService;
import org.remus.giteabot.prworkflow.e2e.SuiteLifecycleMode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@link PrWorkflow} that generates white-box unit tests for the change in a
 * pull request, runs them with the project's own test runner and (optionally)
 * commits them onto the PR branch.
 *
 * <p>Category {@link PrWorkflowCategory#TESTING}; opt-in per bot via the
 * workflow-selection UI. Unlike {@code E2ETestWorkflow} it needs no preview
 * deployment — it operates entirely on a checkout of the PR head branch.</p>
 */
@Slf4j
@Component
public class UnitTestWorkflow implements PrWorkflow {

    public static final String KEY = "unit-test-author";

    static final int ABSOLUTE_MAX_TEST_CASES = 50;
    static final int DEFAULT_MAX_RETRIES = 1;
    static final int DEFAULT_MAX_TEST_CASES = 10;
    static final SuiteLifecycleMode DEFAULT_LIFECYCLE = SuiteLifecycleMode.COMMIT_TO_PR;

    /** Sentinel parameter value meaning "auto-detect the toolchain from the repo". */
    static final String FRAMEWORK_AUTO = "auto";

    private final UnitTestServiceFactory serviceFactory;
    private final WorkflowSelectionService selectionService;

    public UnitTestWorkflow(UnitTestServiceFactory serviceFactory,
                            @Lazy WorkflowSelectionService selectionService) {
        this.serviceFactory = serviceFactory;
        this.selectionService = selectionService;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "AI Unit Tests";
    }

    @Override
    public PrWorkflowCategory category() {
        return PrWorkflowCategory.TESTING;
    }

    @Override
    public WorkflowParamsSchema paramsSchema() {
        return WorkflowParamsSchema.of(
                new WorkflowParamField(UnitTestParam.FRAMEWORK, "Build/test toolchain",
                        false,
                        FRAMEWORK_AUTO,
                        "Leave on auto to detect the toolchain from the repository, or pin it explicitly.",
                        List.of(
                                new WorkflowParamField.EnumOption(FRAMEWORK_AUTO, "Auto-detect",
                                        "Detect from pom.xml / build.gradle / package.json / go.mod / "
                                                + "Cargo.toml / *.csproj / Gemfile / pyproject.toml / Makefile (default)."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.MAVEN.key(), "Maven (JUnit)",
                                        "JVM — runs `mvn test`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.GRADLE.key(), "Gradle (JUnit)",
                                        "JVM — runs `gradle test`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.NPM.key(), "npm (Jest/Vitest)",
                                        "Node.js — runs `npm test`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.PYTEST.key(), "pytest",
                                        "Python — runs `python3 -m pytest`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.GO.key(), "Go",
                                        "Go — runs `go test ./...`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.CARGO.key(), "Cargo (Rust)",
                                        "Rust — runs `cargo test`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.DOTNET.key(), ".NET",
                                        ".NET — runs `dotnet test`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.BUNDLE.key(), "Bundler (Ruby)",
                                        "Ruby — runs `bundle exec rake test`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.MAKE.key(), "Make",
                                        "Generic — runs `make test`."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.GCC.key(), "C (gcc)",
                                        "C — runs `make test` against the project Makefile."),
                                new WorkflowParamField.EnumOption(UnitTestFramework.GPP.key(), "C++ (g++)",
                                        "C++ — runs `make test` against the project Makefile.")
                        )),
                new WorkflowParamField(UnitTestParam.MAX_RETRIES, "Max suite retries",
                        WorkflowParamField.ParamType.INTEGER, false,
                        String.valueOf(DEFAULT_MAX_RETRIES),
                        "How many times to re-run a failing suite before reporting it failed (0-5)."),
                new WorkflowParamField(UnitTestParam.MAX_TEST_CASES, "Max test files per PR",
                        WorkflowParamField.ParamType.INTEGER, false,
                        String.valueOf(DEFAULT_MAX_TEST_CASES),
                        "Cost guard. Capped at " + ABSOLUTE_MAX_TEST_CASES + " regardless of the value."),
                new WorkflowParamField(UnitTestParam.SUITE_LIFECYCLE, "Generated-test lifecycle",
                        false,
                        DEFAULT_LIFECYCLE.key(),
                        "What to do with the generated tests.",
                        List.of(
                                new WorkflowParamField.EnumOption(SuiteLifecycleMode.COMMIT_TO_PR.key(), "Commit to PR",
                                        "Commit the generated tests directly onto the PR branch (default)."),
                                new WorkflowParamField.EnumOption(SuiteLifecycleMode.EPHEMERAL.key(), "Report only",
                                        "Run and report the tests but do not commit them.")
                        ))
        );
    }

    @Override
    public WorkflowResult run(PrWorkflowContext context) {
        Bot bot = context.bot();

        Map<String, Object> params = bot.getWorkflowConfiguration() == null
                ? Map.of()
                : selectionService.resolveParams(bot.getWorkflowConfiguration().getId(), KEY);

        UnitTestFramework framework = resolveFramework(params);
        int maxRetries = clamp(intParam(params, UnitTestParam.MAX_RETRIES, DEFAULT_MAX_RETRIES), 0, 5);
        int maxTestCases = clamp(intParam(params, UnitTestParam.MAX_TEST_CASES, DEFAULT_MAX_TEST_CASES),
                1, ABSOLUTE_MAX_TEST_CASES);
        SuiteLifecycleMode lifecycle = resolveLifecycle(params);

        context.requireActive("before running unit-test author workflow");

        UnitTestService.Request request = new UnitTestService.Request(
                context, framework, maxRetries, maxTestCases, lifecycle);
        UnitTestService.Result result = serviceFactory.create(bot).generate(request);

        return switch (result.status()) {
            case SUCCESS -> WorkflowResult.success(result.summary());
            case SKIPPED -> WorkflowResult.skipped(result.summary());
            case FAILED -> WorkflowResult.failed(result.summary());
        };
    }

    /** Returns {@code null} for "auto", a concrete framework otherwise. */
    private UnitTestFramework resolveFramework(Map<String, Object> params) {
        Object raw = params.get(UnitTestParam.FRAMEWORK.key());
        if (raw == null || raw.toString().isBlank() || FRAMEWORK_AUTO.equalsIgnoreCase(raw.toString().trim())) {
            return null;
        }
        try {
            return UnitTestFramework.fromKey(raw.toString());
        } catch (IllegalArgumentException e) {
            log.warn("[Workflow '{}'] Unknown framework '{}' — falling back to auto-detect", KEY, raw);
            return null;
        }
    }

    private SuiteLifecycleMode resolveLifecycle(Map<String, Object> params) {
        Object raw = params.get(UnitTestParam.SUITE_LIFECYCLE.key());
        if (raw == null || raw.toString().isBlank()) {
            return DEFAULT_LIFECYCLE;
        }
        try {
            SuiteLifecycleMode mode = SuiteLifecycleMode.fromKey(raw.toString());
            // Only COMMIT_TO_PR and EPHEMERAL are meaningful for unit tests today.
            return (mode == SuiteLifecycleMode.COMMIT_TO_PR || mode == SuiteLifecycleMode.EPHEMERAL)
                    ? mode : DEFAULT_LIFECYCLE;
        } catch (IllegalArgumentException e) {
            return DEFAULT_LIFECYCLE;
        }
    }

    private int intParam(Map<String, Object> params,
                         org.remus.giteabot.prworkflow.WorkflowParamName name, int fallback) {
        Object raw = params.get(name.key());
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}


