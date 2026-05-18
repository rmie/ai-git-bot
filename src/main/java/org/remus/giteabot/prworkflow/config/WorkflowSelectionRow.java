package org.remus.giteabot.prworkflow.config;

import org.remus.giteabot.prworkflow.PrWorkflow;

/**
 * Row in the workflow-selection UI. Mirrors
 * {@link org.remus.giteabot.systemsettings.BotToolSelectionRow}.
 *
 * @param workflowKey  stable lower-case identifier from {@code PrWorkflow.key()}
 * @param displayName  human-readable name shown in the admin UI
 * @param category     snapshot of {@link org.remus.giteabot.prworkflow.PrWorkflowCategory}
 *                     or {@code "UNKNOWN"} for orphaned rows
 * @param prWorkflow   the registered workflow bean (or {@code null} if the
 *                     row references a workflow that is no longer registered)
 * @param selected     whether this workflow is enabled in the configuration
 * @param paramsJson   persisted JSON parameters, or {@code null} when the row
 *                     has no params
 */
public record WorkflowSelectionRow(
        String workflowKey,
        String displayName,
        String category,
        PrWorkflow prWorkflow,
        boolean selected,
        String paramsJson) {
}

