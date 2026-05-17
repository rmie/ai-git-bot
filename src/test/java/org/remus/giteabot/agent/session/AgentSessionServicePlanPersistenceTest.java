package org.remus.giteabot.agent.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 7.1 — verifies that {@link AgentSessionService#recordPlan} writes the
 * latest plan summary, raw JSON and timestamp through to the repository, so
 * downstream callers no longer need to walk the conversation history to
 * recover the most recent {@code ImplementationPlan}.
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionServicePlanPersistenceTest {

    @Mock private AgentSessionRepository repository;

    @Test
    void recordPlanWritesAllThreeColumnsAndSaves() {
        AgentSessionService svc = new AgentSessionService(repository);
        AgentSession session = new AgentSession("o", "r", 1L, "title");
        when(repository.save(any(AgentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        AgentSession result = svc.recordPlan(session, "short summary", "{\"summary\":\"short summary\"}");

        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(repository).save(captor.capture());
        AgentSession saved = captor.getValue();
        assertThat(saved.getLastPlanSummary()).isEqualTo("short summary");
        assertThat(saved.getLastPlanJson()).isEqualTo("{\"summary\":\"short summary\"}");
        assertThat(saved.getLastPlanAt()).isAfterOrEqualTo(before);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void recordPlanOverwritesPreviousValuesOnRepeatedCalls() {
        AgentSessionService svc = new AgentSessionService(repository);
        AgentSession session = new AgentSession("o", "r", 1L, "title");
        when(repository.save(any(AgentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.recordPlan(session, "first", "{\"v\":1}");
        svc.recordPlan(session, "second", "{\"v\":2}");

        assertThat(session.getLastPlanSummary()).isEqualTo("second");
        assertThat(session.getLastPlanJson()).isEqualTo("{\"v\":2}");
    }
}

