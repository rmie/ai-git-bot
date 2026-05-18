package org.remus.giteabot.prworkflow.config;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.admin.Bot;
import org.remus.giteabot.admin.BotRepository;
import org.remus.giteabot.prworkflow.PrWorkflow;
import org.remus.giteabot.prworkflow.PrWorkflowCategory;
import org.remus.giteabot.prworkflow.PrWorkflowRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Bootstraps the "Default" {@link WorkflowConfiguration} on application
 * startup and keeps it up to date with newly registered REVIEW workflows.
 *
 * <p>Behaviour (idempotent — safe to run on every boot):</p>
 * <ol>
 *     <li>Ensure exactly one configuration is flagged as
 *     {@link WorkflowConfiguration#isDefaultEntry() default}, creating it on
 *     first boot.</li>
 *     <li>Additively enable every registered {@link PrWorkflow} whose
 *     {@link PrWorkflow#category()} is
 *     {@link PrWorkflowCategory#REVIEW} but that is not yet selected.
 *     Workflows in other categories must be opted in manually via
 *     System settings → Workflow configurations — they are <em>never</em>
 *     auto-enabled.</li>
 *     <li>Backfill {@link Bot#getWorkflowConfiguration()} of every existing
 *     bot to the default configuration so the M2 column has a sensible
 *     value after upgrade.</li>
 * </ol>
 */
@Slf4j
@Component
public class DefaultWorkflowConfigurationInitializer implements ApplicationRunner {

    static final String DEFAULT_NAME = "Default";

    private final WorkflowConfigurationRepository configurationRepository;
    private final WorkflowSelectionService selectionService;
    private final PrWorkflowRegistry workflowRegistry;
    private final BotRepository botRepository;

    public DefaultWorkflowConfigurationInitializer(WorkflowConfigurationRepository configurationRepository,
                                                   WorkflowSelectionService selectionService,
                                                   PrWorkflowRegistry workflowRegistry,
                                                   BotRepository botRepository) {
        this.configurationRepository = configurationRepository;
        this.selectionService = selectionService;
        this.workflowRegistry = workflowRegistry;
        this.botRepository = botRepository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        WorkflowConfiguration defaultConfiguration = ensureDefaultConfiguration();
        enableNewReviewWorkflows(defaultConfiguration);
        backfillExistingBots(defaultConfiguration);
    }

    private WorkflowConfiguration ensureDefaultConfiguration() {
        return configurationRepository.findByDefaultEntryTrue().orElseGet(() -> {
            log.info("Bootstrapping default workflow configuration '{}'", DEFAULT_NAME);
            WorkflowConfiguration created = new WorkflowConfiguration();
            created.setName(DEFAULT_NAME);
            created.setDefaultEntry(true);
            Instant now = Instant.now();
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            return configurationRepository.save(created);
        });
    }

    private void enableNewReviewWorkflows(WorkflowConfiguration defaultConfiguration) {
        List<String> alreadyEnabled = selectionService.enabledWorkflowKeys(defaultConfiguration.getId());
        for (PrWorkflow workflow : workflowRegistry.all()) {
            if (workflow.category() != PrWorkflowCategory.REVIEW) {
                continue;
            }
            if (alreadyEnabled.contains(workflow.key())) {
                continue;
            }
            log.info("Enabling REVIEW workflow '{}' on the default workflow configuration", workflow.key());
            selectionService.enableWorkflow(defaultConfiguration.getId(), workflow.key(), null);
        }
    }

    private void backfillExistingBots(WorkflowConfiguration defaultConfiguration) {
        List<Bot> bots = botRepository.findAll();
        int updated = 0;
        for (Bot bot : bots) {
            if (bot.getWorkflowConfiguration() == null) {
                bot.setWorkflowConfiguration(defaultConfiguration);
                botRepository.save(bot);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Backfilled {} bot(s) to the default workflow configuration", updated);
        }
    }
}

