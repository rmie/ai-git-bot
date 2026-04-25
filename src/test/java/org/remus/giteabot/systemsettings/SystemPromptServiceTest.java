package org.remus.giteabot.systemsettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.remus.giteabot.admin.Bot;
import org.remus.giteabot.admin.BotRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemPromptServiceTest {

    @Mock
    private SystemPromptRepository systemPromptRepository;

    @Mock
    private BotRepository botRepository;

    @InjectMocks
    private SystemPromptService systemPromptService;

    @Test
    void deleteById_defaultEntry_throws() {
        SystemPrompt systemPrompt = new SystemPrompt();
        systemPrompt.setId(1L);
        systemPrompt.setDefaultEntry(true);
        when(systemPromptRepository.findById(1L)).thenReturn(Optional.of(systemPrompt));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> systemPromptService.deleteById(1L));

        assertEquals("The default system prompt cannot be deleted", exception.getMessage());
        verify(systemPromptRepository, never()).delete(any());
    }

    @Test
    void deleteById_promptUsedByBots_throwsWithBotNames() {
        SystemPrompt systemPrompt = new SystemPrompt();
        systemPrompt.setId(2L);
        Bot bot = new Bot();
        bot.setName("Review Bot");
        when(systemPromptRepository.findById(2L)).thenReturn(Optional.of(systemPrompt));
        when(botRepository.findBySystemPromptId(2L)).thenReturn(List.of(bot));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> systemPromptService.deleteById(2L));

        assertTrue(exception.getMessage().contains("Review Bot"));
        verify(systemPromptRepository, never()).delete(any());
    }

    @Test
    void save_requiresBothPromptTexts() {
        SystemPrompt systemPrompt = new SystemPrompt();
        systemPrompt.setName("Custom");
        systemPrompt.setReviewSystemPrompt("review");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> systemPromptService.save(systemPrompt));

        assertEquals("Issue-Agent System-Prompt is required", exception.getMessage());
        verify(systemPromptRepository, never()).save(any());
    }
}
