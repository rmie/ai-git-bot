package org.remus.giteabot.agent.critic;

import org.junit.jupiter.api.Test;
import org.remus.giteabot.ai.AiClient;
import org.remus.giteabot.config.AgentConfigProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 7.3 — verifies the Critic / Reflection step:
 * <ul>
 *   <li>{@code enabled=false} (default) MUST NOT issue any AI call.</li>
 *   <li>JSON outcome parsing maps to {@link ReflectionResult.Outcome}.</li>
 *   <li>Non-JSON / fenced / unparsable responses fail open with APPROVE.</li>
 * </ul>
 */
class CriticAgentTest {

    private final AgentConfigProperties.BudgetConfig budget = new AgentConfigProperties.BudgetConfig();

    @Test
    void disabledNeverCallsAi() {
        AgentConfigProperties.CriticConfig cfg = new AgentConfigProperties.CriticConfig();
        cfg.setEnabled(false);
        AiClient ai = mock(AiClient.class);

        ReflectionResult result = new CriticAgent(cfg, budget, null)
                .review("title", "body", "summary", "diff", ai);

        assertThat(result.outcome()).isEqualTo(ReflectionResult.Outcome.SKIPPED);
        verify(ai, never()).chat(any(), any(), any(), any(), anyInt());
    }

    @Test
    void enabledCallsAiAndParsesIterate() {
        AgentConfigProperties.CriticConfig cfg = new AgentConfigProperties.CriticConfig();
        cfg.setEnabled(true);
        AiClient ai = mock(AiClient.class);
        when(ai.chat(any(), any(), any(), any(), anyInt()))
                .thenReturn("{\"outcome\":\"ITERATE\",\"feedback\":\"add tests\"}");

        ReflectionResult result = new CriticAgent(cfg, budget, null)
                .review("t", "b", "s", "d", ai);

        assertThat(result.outcome()).isEqualTo(ReflectionResult.Outcome.ITERATE);
        assertThat(result.feedback()).isEqualTo("add tests");
    }

    @Test
    void enabledFailOpenOnUnparsableResponse() {
        AgentConfigProperties.CriticConfig cfg = new AgentConfigProperties.CriticConfig();
        cfg.setEnabled(true);
        AiClient ai = mock(AiClient.class);
        when(ai.chat(any(), any(), any(), any(), anyInt())).thenReturn("not json at all");

        ReflectionResult result = new CriticAgent(cfg, budget, null)
                .review("t", "b", "s", "d", ai);

        assertThat(result.outcome()).isEqualTo(ReflectionResult.Outcome.APPROVE);
    }

    @Test
    void enabledStripsCodeFences() {
        AgentConfigProperties.CriticConfig cfg = new AgentConfigProperties.CriticConfig();
        cfg.setEnabled(true);
        AiClient ai = mock(AiClient.class);
        when(ai.chat(any(), any(), any(), any(), anyInt()))
                .thenReturn("```json\n{\"outcome\":\"ABORT\",\"feedback\":\"off scope\"}\n```");

        ReflectionResult result = new CriticAgent(cfg, budget, null)
                .review("t", "b", "s", "d", ai);

        assertThat(result.outcome()).isEqualTo(ReflectionResult.Outcome.ABORT);
        assertThat(result.feedback()).isEqualTo("off scope");
    }

    @Test
    void aiThrowingExceptionApprovesByDefault() {
        AgentConfigProperties.CriticConfig cfg = new AgentConfigProperties.CriticConfig();
        cfg.setEnabled(true);
        AiClient ai = mock(AiClient.class);
        when(ai.chat(any(), any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("network down"));

        ReflectionResult result = new CriticAgent(cfg, budget, null)
                .review("t", "b", "s", "d", ai);

        assertThat(result.outcome()).isEqualTo(ReflectionResult.Outcome.APPROVE);
        assertThat(result.feedback()).contains("network down");
    }
}

