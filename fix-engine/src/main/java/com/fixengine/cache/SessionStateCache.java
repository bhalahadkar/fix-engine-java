package com.fixengine.cache;

import com.fixengine.config.HazelcastConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.SessionID;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Wraps Hazelcast IMap for distributed FIX session state.
 * All 3 cluster nodes share this replicated map (backup-count=2),
 * so failover has full state available immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStateCache {

    private final HazelcastInstance hazelcastInstance;

    // ── Session State ─────────────────────────────────────────────────────

    public void initSession(SessionID sessionId) {
        IMap<String, SessionState> map = getSessionMap();
        map.putIfAbsent(sessionId.toString(), SessionState.initial(sessionId.toString()));
        log.debug("Initialized session state cache for: {}", sessionId);
    }

    public void updateStatus(SessionID sessionId, String status) {
        IMap<String, SessionState> map = getSessionMap();
        map.compute(sessionId.toString(), (k, existing) -> {
            SessionState s = existing != null ? existing : SessionState.initial(k);
            s.setStatus(status);
            s.setLastUpdated(Instant.now());
            return s;
        });
    }

    public void updateSequenceNumbers(SessionID sessionId, int nextSenderSeq, int nextTargetSeq) {
        IMap<String, SessionState> map = getSessionMap();
        map.compute(sessionId.toString(), (k, existing) -> {
            SessionState s = existing != null ? existing : SessionState.initial(k);
            s.setNextSenderSeq(nextSenderSeq);
            s.setNextTargetSeq(nextTargetSeq);
            s.setLastUpdated(Instant.now());
            return s;
        });
        log.debug("Updated seq for {}: sender={} target={}", sessionId, nextSenderSeq, nextTargetSeq);
    }

    public SessionState getSessionState(String sessionId) {
        return getSessionMap().get(sessionId);
    }

    public Collection<SessionState> getAllSessionStates() {
        return getSessionMap().values();
    }

    public Map<String, SessionState> getSessionStateMap() {
        return getSessionMap();
    }

    public int getConnectedSessionCount() {
        return (int) getSessionMap().values().stream()
                .filter(s -> "CONNECTED".equals(s.getStatus()))
                .count();
    }

    public void removeSession(String sessionId) {
        getSessionMap().remove(sessionId);
    }

    private IMap<String, SessionState> getSessionMap() {
        return hazelcastInstance.getMap(HazelcastConfig.SESSION_STATE_MAP);
    }

    // ── Inner State class (must be Serializable for Hazelcast) ────────────

    public static class SessionState implements Serializable {
        private static final long serialVersionUID = 1L;

        private String sessionId;
        private int nextSenderSeq = 1;
        private int nextTargetSeq = 1;
        private String status = "DISCONNECTED";
        private Instant lastUpdated = Instant.now();
        private String activeNodeId;

        public static SessionState initial(String sessionId) {
            SessionState s = new SessionState();
            s.sessionId = sessionId;
            return s;
        }

        // Lombok-free getters/setters for Hazelcast serialization compatibility
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public int getNextSenderSeq() { return nextSenderSeq; }
        public void setNextSenderSeq(int nextSenderSeq) { this.nextSenderSeq = nextSenderSeq; }
        public int getNextTargetSeq() { return nextTargetSeq; }
        public void setNextTargetSeq(int nextTargetSeq) { this.nextTargetSeq = nextTargetSeq; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
        public String getActiveNodeId() { return activeNodeId; }
        public void setActiveNodeId(String activeNodeId) { this.activeNodeId = activeNodeId; }
    }
}
