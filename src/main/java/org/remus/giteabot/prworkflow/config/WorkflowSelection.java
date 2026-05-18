package org.remus.giteabot.prworkflow.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One enabled {@link org.remus.giteabot.prworkflow.PrWorkflow} inside a
 * {@link WorkflowConfiguration}. The workflow is identified by its stable
 * lower-case {@link #workflowKey} as returned by
 * {@code PrWorkflow.key()}.
 *
 * <p>{@link #paramsJson} holds the workflow-specific parameters as a JSON
 * object (see
 * {@link org.remus.giteabot.prworkflow.PrWorkflow#paramsSchema()}) — it is
 * persisted as a JSON-encoded TEXT column and validated by
 * {@link WorkflowParamsValidator} on save. Workflows without parameters
 * persist {@code "{}"}.</p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "workflow_selections",
        uniqueConstraints = @UniqueConstraint(name = "uk_workflow_selection", columnNames = {
                "workflow_configuration_id", "workflow_key"
        }))
public class WorkflowSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_configuration_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private WorkflowConfiguration configuration;

    /** Stable lower-case workflow identifier from {@code PrWorkflow.key()}. */
    @Column(name = "workflow_key", nullable = false, length = 64)
    private String workflowKey;

    /** JSON-encoded parameter map; {@code "{}"} for workflows without params. */
    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson;
}

