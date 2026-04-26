package org.remus.giteabot.agent.writerimpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WriterResponseParserTest {

    private WriterResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new WriterResponseParser();
    }

    @Test
    void parse_withIntroTextBeforeRawJson_parsesStructuredPlan() {
        WriterPlan plan = parser.parse("""
                Now I have enough context. Let me inspect the exact filtering logic.
                {"qualityAssessment":"Ready to continue","requestFiles":[],"requestTools":[{"id":"d1","tool":"rg","args":["author|sender","src","-rn"]}],"clarifyingQuestions":[],"revisedIssueDraft":"","assumptions":[],"openQuestions":[],"readyToCreate":false}
                """);

        assertThat(plan.getQualityAssessment()).isEqualTo("Ready to continue");
        assertThat(plan.getRequestTools()).hasSize(1);
        assertThat(plan.getRequestTools().getFirst().getTool()).isEqualTo("rg");
        assertThat(plan.hasQuestions()).isFalse();
        assertThat(plan.hasContextRequests()).isTrue();
    }

    @Test
    void parse_withIntroTextBeforeJsonCodeFence_parsesStructuredPlan() {
        WriterPlan plan = parser.parse("""
                I found enough context.

                ```json
                {
                  "qualityAssessment": "Missing exact trigger conditions",
                  "clarifyingQuestions": ["Which webhook events should be accepted?"],
                  "readyToCreate": false
                }
                ```
                """);

        assertThat(plan.getQualityAssessment()).isEqualTo("Missing exact trigger conditions");
        assertThat(plan.getClarifyingQuestions()).containsExactly("Which webhook events should be accepted?");
        assertThat(plan.isReadyToCreate()).isFalse();
    }

    @Test
    void parse_plainTextWithoutJson_fallsBackToClarifyingQuestion() {
        WriterPlan plan = parser.parse("Please provide the expected behavior for non-authors.");

        assertThat(plan.getClarifyingQuestions()).containsExactly("Please provide the expected behavior for non-authors.");
        assertThat(plan.hasContextRequests()).isFalse();
    }
}

