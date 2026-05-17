package org.remus.giteabot.agent.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.remus.giteabot.ai.AiMessage;
import org.remus.giteabot.session.ConversationMessage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: when a follow-up comment arrives after a previously aborted /
 * tool-heavy run, the in-DB conversation history contains {@code role:"tool"}
 * messages and (for native function-calling providers) assistant turns whose
 * only payload was {@code tool_calls}. Replaying those naively to OpenAI /
 * Anthropic fails with
 * <em>"messages with role 'tool' must be a response to a preceeding message
 * with 'tool_calls'"</em> because {@link org.remus.giteabot.session.ConversationMessage}
 * does not persist {@code tool_calls} / {@code tool_call_id}. The service must
 * therefore strip these orphaned messages when rebuilding the AI history.
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionServiceToAiMessagesTest {

    @Mock private AgentSessionRepository repository;

    @Test
    void toAiMessages_dropsToolRoleAndBlankAssistantMessages() {
        AgentSessionService svc = new AgentSessionService(repository);
        AgentSession session = new AgentSession("o", "r", 1L, "title");
        addMessageAt(session, "user", "Implement feature X", 1);
        addMessageAt(session, "assistant", "", 2); // tool-call-only turn (tool_calls lost on persistence)
        addMessageAt(session, "tool", "[call_123] some result", 3); // orphaned tool result
        addMessageAt(session, "assistant", "Done with first round.", 4);
        addMessageAt(session, "user", "Please continue", 5);

        List<AiMessage> messages = svc.toAiMessages(session);

        assertThat(messages).extracting(AiMessage::getRole)
                .containsExactly("user", "assistant", "user");
        assertThat(messages).extracting(AiMessage::getContent)
                .containsExactly("Implement feature X", "Done with first round.", "Please continue");
    }

    @Test
    void toAiMessages_keepsNormalConversation() {
        AgentSessionService svc = new AgentSessionService(repository);
        AgentSession session = new AgentSession("o", "r", 1L, "title");
        addMessageAt(session, "user", "Hello", 1);
        addMessageAt(session, "assistant", "Hi", 2);

        List<AiMessage> messages = svc.toAiMessages(session);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo("user");
        assertThat(messages.get(1).getRole()).isEqualTo("assistant");
    }

    private static void addMessageAt(AgentSession session, String role, String content, long offsetSeconds) {
        ConversationMessage msg = new ConversationMessage(role, content);
        msg.setCreatedAt(Instant.ofEpochSecond(1_700_000_000L + offsetSeconds));
        session.getMessages().add(msg);
    }
}



