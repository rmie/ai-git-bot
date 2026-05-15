package org.remus.giteabot.agent.shared;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Step 6 telemetry for the AI tool-calling pipeline.
 *
 * <p>Counters:</p>
 * <ul>
 *     <li>{@code agent.tool_call.mode_total{mode=native|legacy, provider=…}} — one
 *     increment per AI round, indicating which API path the loop took.</li>
 *     <li>{@code agent.tool_call.parse_failures_total{provider=…}} — incremented
 *     whenever the agent parser cannot turn the assistant response (or a
 *     tool-call payload) into a structured plan.</li>
 *     <li>{@code agent.tool_call.latency_seconds{provider=…, mode=…}} — wall
 *     clock for one AI round-trip (chat or chatWithTools).</li>
 * </ul>
 *
 * <p>Like {@link AgentSchemaValidator} this bean exposes itself through a
 * process-wide holder so {@link org.remus.giteabot.agent.loop.AgentLoop}
 * (constructed with {@code new}) can publish without a Spring dependency.
 * If the holder has not been initialised (e.g. unit tests), the helper
 * methods become no-ops.</p>
 */
@Component
public class AgentMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> modeCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> parseFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    @Autowired
    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void publishHolder() {
        AgentMetricsHolder.set(this);
    }

    public void recordToolCallMode(String mode, String provider) {
        String safeMode = normalise(mode);
        String safeProvider = normalise(provider);
        modeCounters.computeIfAbsent(safeMode + "|" + safeProvider, key ->
                Counter.builder("agent.tool_call.mode_total")
                        .description("Number of AI rounds per tool-calling mode and provider")
                        .tag("mode", safeMode)
                        .tag("provider", safeProvider)
                        .register(meterRegistry)
        ).increment();
    }

    public void recordParseFailure(String provider) {
        String safeProvider = normalise(provider);
        parseFailureCounters.computeIfAbsent(safeProvider, key ->
                Counter.builder("agent.tool_call.parse_failures_total")
                        .description("Number of tool-call/plan parse failures per provider")
                        .tag("provider", safeProvider)
                        .register(meterRegistry)
        ).increment();
    }

    public void recordLatency(String mode, String provider, Duration duration) {
        if (duration == null) {
            return;
        }
        String safeMode = normalise(mode);
        String safeProvider = normalise(provider);
        latencyTimers.computeIfAbsent(safeMode + "|" + safeProvider, key ->
                Timer.builder("agent.tool_call.latency_seconds")
                        .description("Wall-clock duration of one AI round (chat or chatWithTools)")
                        .tag("mode", safeMode)
                        .tag("provider", safeProvider)
                        .register(meterRegistry)
        ).record(duration);
    }

    private String normalise(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

