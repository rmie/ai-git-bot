package org.remus.giteabot.agent.shared;

import com.networknt.schema.Error;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Snapshot tests asserting that the bundled JSON-Schemas accept the AI-response
 * shapes our parsers must currently support, and reject obvious malformations.
 */
class AgentSchemaValidatorTest {

    private static AgentSchemaValidator validator;

    @BeforeAll
    static void setUp() {
        validator = AgentSchemaValidator.forTesting();
    }

    @ParameterizedTest(name = "coding snapshot ''{0}'' is valid")
    @ValueSource(strings = {
            "ai-responses/coding/01-runTools.json",
            "ai-responses/coding/02-context-request.json",
            "ai-responses/coding/03-legacy-aliases.json"
    })
    void codingSnapshotsAreSchemaValid(String resource) {
        Optional<List<Error>> result =
                validator.validate(loadResource(resource), AgentSchema.CODING_PLAN);
        assertThat(result).as("expected no schema violations for %s", resource).isEmpty();
    }

    @ParameterizedTest(name = "writer snapshot ''{0}'' is valid")
    @ValueSource(strings = {
            "ai-responses/writer/01-ready.json",
            "ai-responses/writer/02-questions.json",
            "ai-responses/writer/03-context-request.json"
    })
    void writerSnapshotsAreSchemaValid(String resource) {
        Optional<List<Error>> result =
                validator.validate(loadResource(resource), AgentSchema.WRITER_PLAN);
        assertThat(result).as("expected no schema violations for %s", resource).isEmpty();
    }

    @Test
    @DisplayName("Coding schema rejects a tool entry without 'tool' field")
    void codingSchemaRejectsToolWithoutName() {
        String invalid = "{\"summary\":\"x\",\"runTools\":[{\"id\":\"t1\"}]}";
        Optional<List<Error>> result =
                validator.validate(invalid, AgentSchema.CODING_PLAN);
        assertThat(result).isPresent();
        assertThat(result.get()).isNotEmpty();
    }

    @Test
    @DisplayName("Writer schema rejects empty objects (no recognised property)")
    void writerSchemaRejectsEmpty() {
        Optional<List<Error>> result =
                validator.validate("{}", AgentSchema.WRITER_PLAN);
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Validator returns empty Optional for blank input (nothing to validate)")
    void blankInputIsNoOp() {
        assertThat(validator.validate("", AgentSchema.CODING_PLAN)).isEmpty();
        assertThat(validator.validate(null, AgentSchema.WRITER_PLAN)).isEmpty();
    }

    @Test
    @DisplayName("Non-enforce mode is the default")
    void enforceDefaultsToFalse() {
        // forTesting() builds a validator without AgentConfigProperties so
        // isEnforce() must be false.
        assertThat(validator.isEnforce()).isFalse();
    }

    private String loadResource(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load test resource: " + path, e);
        }
    }
}

