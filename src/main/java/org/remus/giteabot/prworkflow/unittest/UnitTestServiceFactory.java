package org.remus.giteabot.prworkflow.unittest;

import org.remus.giteabot.admin.AiClientFactory;
import org.remus.giteabot.admin.Bot;
import org.remus.giteabot.admin.GiteaClientFactory;
import org.remus.giteabot.agent.validation.WorkspaceService;
import org.remus.giteabot.ai.AiClient;
import org.remus.giteabot.prworkflow.unittest.agents.UnitTestAuthorAgent;
import org.remus.giteabot.prworkflow.unittest.runner.UnitTestRunner;
import org.remus.giteabot.repository.RepositoryApiClient;
import org.springframework.stereotype.Component;

/**
 * Factory for per-bot {@link UnitTestService} instances. Resolves the bot's AI
 * and Git clients and wires the shared singleton collaborators, mirroring
 * {@code AgentReviewServiceFactory}.
 */
@Component
public class UnitTestServiceFactory {

    private final AiClientFactory aiClientFactory;
    private final GiteaClientFactory giteaClientFactory;
    private final WorkspaceService workspaceService;
    private final FrameworkDetector frameworkDetector;
    private final UnitTestAuthorAgent authorAgent;
    private final UnitTestRunner runner;
    private final UnitTestSuiteRepository suiteRepository;

    public UnitTestServiceFactory(AiClientFactory aiClientFactory,
                                  GiteaClientFactory giteaClientFactory,
                                  WorkspaceService workspaceService,
                                  FrameworkDetector frameworkDetector,
                                  UnitTestAuthorAgent authorAgent,
                                  UnitTestRunner runner,
                                  UnitTestSuiteRepository suiteRepository) {
        this.aiClientFactory = aiClientFactory;
        this.giteaClientFactory = giteaClientFactory;
        this.workspaceService = workspaceService;
        this.frameworkDetector = frameworkDetector;
        this.authorAgent = authorAgent;
        this.runner = runner;
        this.suiteRepository = suiteRepository;
    }

    public UnitTestService create(Bot bot) {
        if (bot.getSystemPrompt() == null) {
            throw new IllegalStateException("Bot must have a system prompt assigned");
        }
        AiClient aiClient = aiClientFactory.getClient(bot.getAiIntegration());
        RepositoryApiClient repoClient = giteaClientFactory.getApiClient(bot.getGitIntegration());
        return new UnitTestService(repoClient, aiClient, bot.getSystemPrompt(),
                workspaceService, frameworkDetector, authorAgent, runner, suiteRepository);
    }
}



