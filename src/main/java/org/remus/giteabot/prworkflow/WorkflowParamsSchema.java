package org.remus.giteabot.prworkflow;

import java.util.List;
import java.util.Objects;

/**
 * Declarative description of the parameters accepted by a {@link PrWorkflow}.
 *
 * <p>Exposed via {@link PrWorkflow#paramsSchema()} and consumed by:</p>
 * <ul>
 *     <li>the workflow-configuration admin UI, which renders one HTML input
 *     per {@link WorkflowParamField};</li>
 *     <li>the
 *     {@code org.remus.giteabot.prworkflow.config.WorkflowParamsValidator},
 *     which checks the persisted {@code params_json} against this schema
 *     before a workflow run.</li>
 * </ul>
 *
 * <p>Workflows without any parameters return {@link #empty()}.</p>
 */
public record WorkflowParamsSchema(List<WorkflowParamField> fields) {

    public WorkflowParamsSchema {
        fields = List.copyOf(fields == null ? List.of() : fields);
    }

    private static final WorkflowParamsSchema EMPTY = new WorkflowParamsSchema(List.of());

    public static WorkflowParamsSchema empty() {
        return EMPTY;
    }

    public static WorkflowParamsSchema of(WorkflowParamField... fields) {
        return new WorkflowParamsSchema(List.of(fields));
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public WorkflowParamField require(String name) {
        Objects.requireNonNull(name, "name");
        return fields.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown parameter '" + name + "'"));
    }
}

