package org.remus.giteabot.prworkflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrWorkflowRegistryTest {

    @Test
    void indexesWorkflowsByLowercaseKey() {
        PrWorkflow review = stub("review", PrWorkflowCategory.REVIEW);
        PrWorkflow e2e = stub("e2e-test", PrWorkflowCategory.TESTING);
        PrWorkflowRegistry registry = new PrWorkflowRegistry(List.of(review, e2e));

        assertSame(review, registry.require("review"));
        assertSame(e2e, registry.require("e2e-test"));
        assertSame(e2e, registry.require("E2E-Test")); // case-insensitive lookup
        assertEquals(2, registry.all().size());
    }

    @Test
    void rejectsBlankKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new PrWorkflowRegistry(List.of(stub("", PrWorkflowCategory.REVIEW))));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void rejectsNonLowercaseKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new PrWorkflowRegistry(List.of(stub("Review", PrWorkflowCategory.REVIEW))));
        assertTrue(ex.getMessage().contains("kebab-case"));
    }

    @Test
    void rejectsDuplicateKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new PrWorkflowRegistry(List.of(
                        stub("review", PrWorkflowCategory.REVIEW),
                        stub("review", PrWorkflowCategory.REVIEW))));
        assertTrue(ex.getMessage().contains("Duplicate"));
    }

    @Test
    void requireThrowsForUnknownKey() {
        PrWorkflowRegistry registry = new PrWorkflowRegistry(List.of(stub("review", PrWorkflowCategory.REVIEW)));
        assertThrows(IllegalArgumentException.class, () -> registry.require("does-not-exist"));
    }

    private static PrWorkflow stub(String key, PrWorkflowCategory category) {
        return new PrWorkflow() {
            @Override public String key() { return key; }
            @Override public String displayName() { return "Stub " + key; }
            @Override public PrWorkflowCategory category() { return category; }
            @Override public WorkflowResult run(PrWorkflowContext context) {
                return WorkflowResult.success("ok");
            }
        };
    }
}
