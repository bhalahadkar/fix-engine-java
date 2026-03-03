package com.fixengine.core;

import com.fixengine.cache.SessionStateCache;
import com.fixengine.entity.FixMessage;
import com.fixengine.repository.FixMessageRepository;
import com.fixengine.websocket.LiveMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;

import java.time.Instant;

/**
 * Central QuickFIX/J Application callback handler.
 * Receives all lifecycle and message events from the FIX engine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixApplicationAdapter implements Application {

    private final FixMessageRepository messageRepository;
    private final SessionStateCache sessionStateCache;
    private final FixMessageParser messageParser;
    private final LiveMessagePublisher livePublisher;
    private final FixMessageRouter messageRouter;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("FIX Session created: {}", sessionId);
        sessionStateCache.initSession(sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("FIX Session LOGON: {}", sessionId);
        sessionStateCache.updateStatus(sessionId, "CONNECTED");
        syncSequenceNumbers(sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.warn("FIX Session LOGOUT: {}", sessionId);
        sessionStateCache.updateStatus(sessionId, "DISCONNECTED");
        syncSequenceNumbers(sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        log.debug("toAdmin [{}]: {}", sessionId, message.getClass().getSimpleName());
        persistMessage(message, sessionId, "O");
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("fromAdmin [{}]: {}", sessionId, message.getClass().getSimpleName());
        persistMessage(message, sessionId, "I");
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("toApp [{}]", sessionId);
        persistMessage(message, sessionId, "O");
        syncSequenceNumbers(sessionId);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.debug("fromApp [{}]", sessionId);
        persistMessage(message, sessionId, "I");
        syncSequenceNumbers(sessionId);
        messageRouter.route(message, sessionId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void persistMessage(Message message, SessionID sessionId, String direction) {
        try {
            FixMessage entity = messageParser.toEntity(message, sessionId, direction);
            messageRepository.save(entity);
            livePublisher.publish(entity);
        } catch (Exception e) {
            log.error("Failed to persist FIX message for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    private void syncSequenceNumbers(SessionID sessionId) {
        try {
            Session session = Session.lookupSession(sessionId);
            if (session != null) {
                int nextSender = session.getExpectedSenderNum();
                int nextTarget = session.getExpectedTargetNum();
                sessionStateCache.updateSequenceNumbers(sessionId, nextSender, nextTarget);
                log.debug("Synced seq numbers for {}: sender={} target={}", sessionId, nextSender, nextTarget);
            }
        } catch (Exception e) {
            log.error("Failed to sync sequence numbers for {}: {}", sessionId, e.getMessage());
        }
    }
}
