package org.remus.giteabot.agent.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Step 7.1 — verifies that {@link AgentSessionService#recordPlan} writes the
 * latest plan summary, raw JSON and timestamp on the managed entity, so
 * downstream callers no longer need to walk the conversation history to
 * recover the most recent {@code ImplementationPlan}.
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionServicePlanPersistenceTest {

    @Mock private AgentSessionRepository repository;

    @Test
    void recordPlanWritesAllThreeColumnsAndSaves() {
        AgentSession managed = new AgentSession("o", "r", 1L, "title");
        managed.setId(42L);
        when(repository.getReferenceById(42L)).thenReturn(managed);

        AgentSessionService svc = new AgentSessionService(repository);
        AgentSession session = new AgentSession("o", "r", 1L, "title");
        session.setId(42L);

        Instant before = Instant.now();
        AgentSession result = svc.recordPlan(session, "short summary", "{\"summary\":\"short summary\"}");

        assertThat(result.getLastPlanSummary()).isEqualTo("short summary");
        assertThat(result.getLastPlanJson()).isEqualTo("{\"summary\":\"short summary\"}");
        assertThat(result.getLastPlanAt()).isAfterOrEqualTo(before);
        assertThat(result).isSameAs(managed);
    }

    @Test
    void recordPlanOverwritesPreviousValuesOnRepeatedCalls() {
        AgentSession managed = new AgentSession("o", "r", 1L, "title");
        managed.setId(42L);
        when(repository.getReferenceById(42L)).thenReturn(managed);

        AgentSessionService svc = new AgentSessionService(repository);
        AgentSession session = new AgentSession("o", "r", 1L, "title");
        session.setId(42L);

        svc.recordPlan(session, "first", "{\"v\":1}");
        svc.recordPlan(session, "second", "{\"v\":2}");

        assertThat(managed.getLastPlanSummary()).isEqualTo("second");
        assertThat(managed.getLastPlanJson()).isEqualTo("{\"v\":2}");
    }
}
