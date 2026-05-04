package org.remus.giteabot.systemsettings;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemSettingsControllerTest {

    @Test
    void preview_missingPrompt_returnsNotFound() {
        SystemPromptService systemPromptService = mock(SystemPromptService.class);
        McpConfigurationService mcpConfigurationService = mock(McpConfigurationService.class);
        when(systemPromptService.findById(99L)).thenReturn(Optional.empty());
        SystemSettingsController controller = new SystemSettingsController(systemPromptService, mcpConfigurationService);

        assertEquals(404, controller.preview(99L).getStatusCode().value());
    }
}
