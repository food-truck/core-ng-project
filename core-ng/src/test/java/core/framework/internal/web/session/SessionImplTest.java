package core.framework.internal.web.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author neo
 */
class SessionImplTest {
    private SessionImpl session;

    @BeforeEach
    void createSessionImpl() {
        session = new SessionImpl("localhost");
    }

    @Test
    void set() {
        session.set("key", "value");
        assertThat(session.changedFields).containsOnly("key");
        assertThat(session.get("key")).get().isEqualTo("value");

        session.set("key", null);
        assertThat(session.changedFields).containsOnly("key");
        assertThat(session.get("key")).isNotPresent();
    }

    @Test
    void setWithoutChange() {
        session.set("key", null);
        assertThat(session.changedFields).isEmpty();

        session.values.put("key", "value");
        session.set("key", "value");
        assertThat(session.changedFields).isEmpty();
    }

    @Test
    void setWithReservedKey() {
        assertThatThrownBy(() -> session.set(SessionImpl.TIMEOUT_FIELD, "value"))
            .isInstanceOf(Error.class)
            .hasMessageContaining("key must not start with '_'");
    }

    @Test
    void timeout() {
        session.timeout(Duration.ofSeconds(30));
        assertThat(session.changedFields).contains(SessionImpl.TIMEOUT_FIELD);
    }

    @Test
    void id() {
        session.id("sessionId");
        assertThat(session.hash).isNotNull();
    }
}
