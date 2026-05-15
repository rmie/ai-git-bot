package org.remus.giteabot.agent.loop;

/**
 * How an {@link AgentStrategy} wants the {@link AgentLoop} to talk to the
 * underlying {@link org.remus.giteabot.ai.AiClient}.
 *
 * <p>Step 6: a strategy may opt in to native function/tool calling by
 * returning {@link #NATIVE} from {@link AgentStrategy#preferredToolMode()}
 * and supplying non-empty {@link AgentStrategy#toolDescriptors()}. The loop
 * still falls back to {@link #LEGACY} (text {@code chat}) when the
 * configured client reports
 * {@link org.remus.giteabot.ai.AiClient#supportsNativeTools() supportsNativeTools()
 * == false} — typically because the operator flipped the per-integration
 * {@code use_legacy_tool_calling} switch.</p>
 */
public enum ToolingMode {
    LEGACY,
    NATIVE
}

