package org.remus.giteabot.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 7.2 — sanity-checks for the new {@link AgentConfigProperties.BudgetConfig}
 * and the legacy migration performed by
 * {@link AgentConfigProperties#applyLegacyBudgetDefaults()}.
 */
class AgentConfigPropertiesBudgetTest {

    @Test
    void budgetDefaultsMatchSpec() {
        AgentConfigProperties.BudgetConfig b = new AgentConfigProperties.BudgetConfig();
        assertThat(b.getMaxRounds()).isEqualTo(10);
        assertThat(b.getMaxContextRounds()).isEqualTo(3);
        assertThat(b.getMaxValidationRetries()).isEqualTo(3);
        assertThat(b.getMaxContextToolRequestsPerRound()).isEqualTo(5);
        assertThat(b.getMaxTokensPerCall()).isEqualTo(16384);
    }

    @Test
    void legacyMaxTokensPropagatesIntoBudgetWhenBudgetIsAtDefault() {
        AgentConfigProperties props = new AgentConfigProperties();
        props.setMaxTokens(4096);                     // legacy field still set
        props.applyLegacyBudgetDefaults();             // simulate @PostConstruct
        assertThat(props.getBudget().getMaxTokensPerCall()).isEqualTo(4096);
    }

    @Test
    void explicitBudgetOverridesLegacyMaxTokens() {
        AgentConfigProperties props = new AgentConfigProperties();
        props.setMaxTokens(4096);
        props.getBudget().setMaxTokensPerCall(9999);   // operator opted into the new knob
        props.applyLegacyBudgetDefaults();
        assertThat(props.getBudget().getMaxTokensPerCall()).isEqualTo(9999);
    }

    @Test
    void legacyValidationRetriesPropagateWhenEnabled() {
        AgentConfigProperties props = new AgentConfigProperties();
        props.getValidation().setEnabled(true);
        props.getValidation().setMaxRetries(7);
        props.applyLegacyBudgetDefaults();
        assertThat(props.getBudget().getMaxValidationRetries()).isEqualTo(7);
    }

    @Test
    void criticDefaultsAreOff() {
        AgentConfigProperties.CriticConfig c = new AgentConfigProperties.CriticConfig();
        assertThat(c.isEnabled()).isFalse();
        assertThat(c.getMaxIterations()).isEqualTo(1);
        assertThat(c.getRequireApprovalFor()).isEmpty();
    }
}

