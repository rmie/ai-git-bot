package org.remus.giteabot.prworkflow;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PrWorkflowMetricsTest {

    private SimpleMeterRegistry registry;
    private PrWorkflowMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PrWorkflowMetrics(registry);
    }

    @Test
    void recordRun_incrementsTaggedCounterAndTimer() {
        metrics.recordRun("test-workflow", PrWorkflowRunStatus.SUCCESS, Duration.ofMillis(500));

        Counter c = registry.find("prworkflow.run_total")
                .tag("workflow", "test-workflow")
                .tag("status", "success").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        Timer t = registry.find("prworkflow.run_duration_seconds")
                .tag("workflow", "test-workflow").timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1L);
        assertThat(t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(500.0);
    }

    @Test
    void recordRun_handlesNulls() {
        metrics.recordRun(null, null, null);

        Counter c = registry.find("prworkflow.run_total")
                .tag("workflow", "unknown")
                .tag("status", "unknown").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        Timer t = registry.find("prworkflow.run_duration_seconds")
                .tag("workflow", "unknown").timer();
        assertThat(t).isNull();
    }
}
