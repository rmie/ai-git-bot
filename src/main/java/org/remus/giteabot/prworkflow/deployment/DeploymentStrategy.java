package org.remus.giteabot.prworkflow.deployment;

/**
 * Service Provider Interface for one way of triggering and observing a
 * per-PR preview deployment. Implementations live alongside this interface
 * under {@code org.remus.giteabot.prworkflow.deployment} and are discovered
 * automatically via Spring component scanning.
 *
 * <p>Strategies are stateless beans — all per-deployment state must travel
 * inside {@link DeploymentResult#handleJson()} and gets round-tripped back
 * into the next call via the persisted
 * {@code pr_workflow_runs.deployment_handle_json} column.</p>
 *
 * <p>See {@code doc/PR_WORKFLOWS.md} § "Deployment targets" for the JSON
 * payload schemas on the wire.</p>
 */
public interface DeploymentStrategy {

    /**
     * Stable enum-derived key matched against
     * {@code deployment_targets.strategy_type}. Two strategies must never
     * share a key; this is enforced by {@link DeploymentStrategyRegistry}.
     */
    DeploymentStrategyType typeKey();

    /**
     * Triggers one deployment for the given PR/SHA. Implementations must NOT
     * block waiting on remote completion — return
     * {@link DeploymentResult#pending(String)} and rely on the inbound
     * callback (or {@link #poll(org.remus.giteabot.prworkflow.PrWorkflowRun)})
     * instead. The orchestrator's await/poll loop handles the timeout.
     */
    DeploymentResult trigger(DeploymentRequest request);

    /**
     * Optional polling hook. Default returns whatever the run already knows
     * (i.e. trust the inbound callback). Strategies whose remote side does
     * not support callbacks should override this.
     */
    default DeploymentResult poll(org.remus.giteabot.prworkflow.PrWorkflowRun run) {
        if (run.getPreviewUrl() != null && !run.getPreviewUrl().isBlank()) {
            return DeploymentResult.ready(run.getPreviewUrl(), run.getDeploymentHandleJson());
        }
        return DeploymentResult.pending(run.getDeploymentHandleJson());
    }

    /**
     * Optional teardown hook invoked by {@code BotWebhookService.handlePrClosed}.
     * Default no-op so strategies whose remote side cleans up on its own
     * (Vercel/Netlify) don't have to override.
     */
    default void teardown(org.remus.giteabot.prworkflow.PrWorkflowRun run) {
        // no-op default
    }

    /**
     * {@code true} if the strategy hands off to a remote system that will
     * deliver an asynchronous callback. The orchestrator only spins up the
     * per-run notification queue when this is true.
     */
    default boolean awaitsCallback() {
        return false;
    }
}

