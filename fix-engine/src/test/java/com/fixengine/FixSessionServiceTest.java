package com.fixengine;

import com.fixengine.cache.SessionStateCache;
import com.fixengine.config.FixEngineProperties;
import com.fixengine.entity.FixSession;
import com.fixengine.repository.FixSessionRepository;
import com.fixengine.service.FixSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixSessionServiceTest {

    @Mock private FixSessionRepository sessionRepository;
    @Mock private SessionStateCache sessionStateCache;
    @Mock private FixEngineProperties properties;

    @InjectMocks
    private FixSessionService sessionService;

    @Test
    void shouldCreateSession() {
        FixSession session = buildSession("TEST-001", "INITIATOR");
        when(sessionRepository.save(any())).thenReturn(session);

        FixSession result = sessionService.createSession(session);

        assertThat(result.getSessionId()).isEqualTo("TEST-001");
        verify(sessionRepository).save(session);
    }

    @Test
    void shouldThrowWhenSessionNotFound() {
        when(sessionRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void shouldBuildDtoWithCacheOverlay() {
        FixSession session = buildSession("S1", "ACCEPTOR");
        when(sessionRepository.findById("S1")).thenReturn(Optional.of(session));

        SessionStateCache.SessionState state = SessionStateCache.SessionState.initial("S1");
        state.setStatus("CONNECTED");
        state.setNextSenderSeq(100);
        state.setNextTargetSeq(95);
        when(sessionStateCache.getSessionState("S1")).thenReturn(state);

        var dto = sessionService.toDto(session);

        assertThat(dto.getStatus()).isEqualTo("CONNECTED");
        assertThat(dto.getNextSenderSeq()).isEqualTo(100);
        assertThat(dto.getNextTargetSeq()).isEqualTo(95);
    }

    private FixSession buildSession(String id, String mode) {
        FixSession s = new FixSession();
        s.setSessionId(id);
        s.setBeginString("FIX.4.4");
        s.setSenderCompId("MY_FIRM");
        s.setTargetCompId("BROKER_X");
        s.setMode(mode);
        s.setPort(9876);
        s.setEnabled(true);
        return s;
    }
}
