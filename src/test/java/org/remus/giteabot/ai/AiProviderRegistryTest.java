package org.remus.giteabot.ai;

import org.junit.jupiter.api.Test;
import org.remus.giteabot.ai.google.GoogleAiProviderMetadata;
import org.remus.giteabot.ai.ollama.OllamaProviderMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderRegistryTest {

    @Test
    void googleMetadataExposesDisplayNameAndApiKeyRequirement() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                new GoogleAiProviderMetadata(),
                new OllamaProviderMetadata()
        ));

        assertEquals("gemini", registry.getDisplayNames().get("google"));
        assertTrue(registry.getApiKeyRequirements().get("google"));
        assertFalse(registry.getApiKeyRequirements().get("ollama"));
    }
}
