package org.remus.giteabot.prworkflow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.remus.giteabot.admin.Bot;
import org.remus.giteabot.admin.BotRepository;
import org.remus.giteabot.prworkflow.PrWorkflow;
import org.remus.giteabot.prworkflow.PrWorkflowCategory;
import org.remus.giteabot.prworkflow.PrWorkflowContext;
import org.remus.giteabot.prworkflow.PrWorkflowRegistry;
import org.remus.giteabot.prworkflow.WorkflowResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultWorkflowConfigurationInitializerTest {

    @Mock private WorkflowConfigurationRepository configurationRepository;
    @Mock private WorkflowSelectionService selectionService;
    @Mock private BotRepository botRepository;

    private PrWorkflowRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PrWorkflowRegistry(List.of(new ReviewLike(), new TestsLike()));
    }

    private DefaultWorkflowConfigurationInitializer newInitializer() {
        return new DefaultWorkflowConfigurationInitializer(configurationRepository,
                selectionService, registry, botRepository);
    }

    @Test
    void run_bootstrapsDefault_whenAbsent_andEnablesReviewWorkflowsOnly() {
        when(configurationRepository.findByDefaultEntryTrue()).thenReturn(Optional.empty());
        WorkflowConfiguration created = new WorkflowConfiguration();
        created.setId(7L);
        created.setName(DefaultWorkflowConfigurationInitializer.DEFAULT_NAME);
        created.setDefaultEntry(true);
        when(configurationRepository.save(any(WorkflowConfiguration.class))).thenReturn(created);
        when(selectionService.enabledWorkflowKeys(7L)).thenReturn(List.of());
        when(botRepository.findAll()).thenReturn(List.of());

        newInitializer().run(null);

        ArgumentCaptor<WorkflowConfiguration> savedCfg = ArgumentCaptor.forClass(WorkflowConfiguration.class);
        verify(configurationRepository).save(savedCfg.capture());
        assertEquals(DefaultWorkflowConfigurationInitializer.DEFAULT_NAME, savedCfg.getValue().getName());
        assertTrue(savedCfg.getValue().isDefaultEntry());

        // REVIEW is auto-enabled; TESTING is not.
        verify(selectionService).enableWorkflow(eq(7L), eq("review-like"), any());
        verify(selectionService, never()).enableWorkflow(eq(7L), eq("tests-like"), any());
    }

    @Test
    void run_isIdempotent_doesNotReEnableExistingWorkflows() {
        WorkflowConfiguration existing = new WorkflowConfiguration();
        existing.setId(7L);
        existing.setName(DefaultWorkflowConfigurationInitializer.DEFAULT_NAME);
        existing.setDefaultEntry(true);
        when(configurationRepository.findByDefaultEntryTrue()).thenReturn(Optional.of(existing));
        when(selectionService.enabledWorkflowKeys(7L)).thenReturn(List.of("review-like"));
        when(botRepository.findAll()).thenReturn(List.of());

        newInitializer().run(null);

        verify(configurationRepository, never()).save(any());
        verify(selectionService, never()).enableWorkflow(any(), any(), any());
    }

    @Test
    void run_backfillsBotsWithoutWorkflowConfiguration() {
        WorkflowConfiguration existing = new WorkflowConfiguration();
        existing.setId(7L);
        existing.setDefaultEntry(true);
        existing.setName(DefaultWorkflowConfigurationInitializer.DEFAULT_NAME);
        when(configurationRepository.findByDefaultEntryTrue()).thenReturn(Optional.of(existing));
        when(selectionService.enabledWorkflowKeys(7L)).thenReturn(List.of("review-like"));

        Bot withoutConfig = new Bot();
        withoutConfig.setName("LegacyBot");
        Bot withConfig = new Bot();
        withConfig.setName("ModernBot");
        WorkflowConfiguration other = new WorkflowConfiguration();
        other.setId(99L);
        withConfig.setWorkflowConfiguration(other);
        when(botRepository.findAll()).thenReturn(List.of(withoutConfig, withConfig));

        newInitializer().run(null);

        verify(botRepository, times(1)).save(any(Bot.class));
        verify(botRepository).save(withoutConfig);
        assertSame(existing, withoutConfig.getWorkflowConfiguration());
        // Bot already configured is left untouched.
        assertSame(other, withConfig.getWorkflowConfiguration());
    }

    private static final class ReviewLike implements PrWorkflow {
        @Override public String key() { return "review-like"; }
        @Override public String displayName() { return "Review-like"; }
        @Override public PrWorkflowCategory category() { return PrWorkflowCategory.REVIEW; }
        @Override public WorkflowResult run(PrWorkflowContext context) { return WorkflowResult.skipped("noop"); }
    }

    private static final class TestsLike implements PrWorkflow {
        @Override public String key() { return "tests-like"; }
        @Override public String displayName() { return "Tests-like"; }
        @Override public PrWorkflowCategory category() { return PrWorkflowCategory.TESTING; }
        @Override public WorkflowResult run(PrWorkflowContext context) { return WorkflowResult.skipped("noop"); }
    }
}


