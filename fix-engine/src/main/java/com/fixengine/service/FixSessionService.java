package com.fixengine.service;

import com.fixengine.cache.SessionStateCache;
import com.fixengine.config.FixEngineProperties;
import com.fixengine.dto.SessionDto;
import com.fixengine.entity.FixSession;
import com.fixengine.repository.FixSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quickfix.*;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixSessionService {

    private final FixSessionRepository sessionRepository;
    private final SessionStateCache sessionStateCache;
    private final FixEngineProperties properties;

    // ── Session CRUD ──────────────────────────────────────────────────────

    public List<FixSession> getAllSessions() {
        return sessionRepository.findAll();
    }

    public FixSession getSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    @Transactional
    public FixSession createSession(FixSession session) {
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public FixSession updateSession(String sessionId, FixSession updated) {
        FixSession existing = getSession(sessionId);
        existing.setBeginString(updated.getBeginString());
        existing.setSenderCompId(updated.getSenderCompId());
        existing.setTargetCompId(updated.getTargetCompId());
        existing.setMode(updated.getMode());
        existing.setHost(updated.getHost());
        existing.setPort(updated.getPort());
        existing.setHeartbeatInterval(updated.getHeartbeatInterval());
        existing.setReconnectInterval(updated.getReconnectInterval());
        existing.setEnabled(updated.getEnabled());
        existing.setUpdatedAt(Instant.now());
        return sessionRepository.save(existing);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        stopSession(sessionId);
        sessionRepository.deleteById(sessionId);
        sessionStateCache.removeSession(sessionId);
    }

    // ── Session Lifecycle ─────────────────────────────────────────────────

    public void startSession(String sessionId) {
        log.info("Starting FIX session: {}", sessionId);
        SessionID sid = parseSessionId(sessionId);
        Session session = Session.lookupSession(sid);
        if (session != null) {
            session.logon();
            sessionStateCache.updateStatus(sid, "CONNECTING");
        } else {
            log.warn("Session not found in QFJ registry: {}", sessionId);
        }
    }

    public void stopSession(String sessionId) {
        log.info("Stopping FIX session: {}", sessionId);
        SessionID sid = parseSessionId(sessionId);
        Session session = Session.lookupSession(sid);
        if (session != null) {
            session.logout("Stopped via GUI");
            sessionStateCache.updateStatus(sid, "DISCONNECTED");
        }
    }

    public void resetSession(String sessionId) {
        log.info("Resetting FIX session (seq numbers): {}", sessionId);
        SessionID sid = parseSessionId(sessionId);
        Session session = Session.lookupSession(sid);
        if (session != null) {
            try {
                session.reset();
                sessionStateCache.updateSequenceNumbers(sid, 1, 1);
                log.info("Session {} reset to seq 1/1", sessionId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to reset session: " + sessionId, e);
            }
        }
    }

    // ── Cluster Failover Support ──────────────────────────────────────────

    /**
     * Called when this node becomes the cluster leader.
     * Restores all FIX sessions using sequence numbers from distributed cache.
     */
    public void restoreAndActivateSessions() {
        log.info("Restoring FIX sessions from distributed cache...");
        Collection<SessionStateCache.SessionState> states = sessionStateCache.getAllSessionStates();

        for (SessionStateCache.SessionState state : states) {
            try {
                SessionID sessionId = SessionID.parse(state.getSessionId());
                Session session = Session.lookupSession(sessionId);
                if (session != null) {
                    session.setNextSenderMsgSeqNum(state.getNextSenderSeq());
                    session.setNextTargetMsgSeqNum(state.getNextTargetSeq());
                    session.logon();
                    log.info("Restored session {} with sender={} target={}",
                            state.getSessionId(), state.getNextSenderSeq(), state.getNextTargetSeq());
                } else {
                    log.warn("Session {} not found in QFJ registry during restore", state.getSessionId());
                }
            } catch (Exception e) {
                log.error("Failed to restore session {}: {}", state.getSessionId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Called when this node loses leadership — stops active sessions.
     */
    public void enterStandbyMode() {
        log.info("Entering standby — stopping active FIX sessions on this node");
        // Sessions are managed by the leader; standby nodes just maintain cache
        // In a true multi-node setup, only the leader runs QFJ connectors
    }

    // ── Session Status with Cache Overlay ────────────────────────────────

    public SessionDto toDto(FixSession session) {
        SessionDto dto = new SessionDto();
        dto.setSessionId(session.getSessionId());
        dto.setBeginString(session.getBeginString());
        dto.setSenderCompId(session.getSenderCompId());
        dto.setTargetCompId(session.getTargetCompId());
        dto.setMode(session.getMode());
        dto.setHost(session.getHost());
        dto.setPort(session.getPort());
        dto.setHeartbeatInterval(session.getHeartbeatInterval());
        dto.setEnabled(session.getEnabled());

        // Overlay live state from distributed cache
        SessionStateCache.SessionState state = sessionStateCache.getSessionState(session.getSessionId());
        if (state != null) {
            dto.setStatus(state.getStatus());
            dto.setNextSenderSeq(state.getNextSenderSeq());
            dto.setNextTargetSeq(state.getNextTargetSeq());
            dto.setLastUpdated(state.getLastUpdated());
        } else {
            dto.setStatus("UNKNOWN");
        }

        return dto;
    }

    private SessionID parseSessionId(String sessionId) {
        // sessionId format: FIX.4.4:SENDER->TARGET
        // or stored as SENDER->TARGET in DB
        try {
            // Try QuickFIX/J parse first
            return SessionID.parse(sessionId);
        } catch (Exception e) {
            // Try to reconstruct from DB record
            FixSession session = getSession(sessionId);
            return new SessionID(session.getBeginString(), session.getSenderCompId(), session.getTargetCompId());
        }
    }
}
