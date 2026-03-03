package com.fixengine.websocket;

import com.fixengine.entity.FixMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes FIX messages to WebSocket subscribers in real time.
 * GUI subscribes to /topic/messages for live feed.
 * GUI subscribes to /topic/sessions/{sessionId} for per-session events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveMessagePublisher {

    private final SimpMessagingTemplate messaging;

    public void publish(FixMessage message) {
        try {
            // Broadcast to all live feed subscribers
            Map<String, Object> payload = Map.of(
                "id",            message.getId(),
                "sessionId",     message.getSessionId(),
                "senderCompId",  message.getSenderCompId(),
                "targetCompId",  message.getTargetCompId(),
                "msgType",       message.getMsgType(),
                "msgSeqNum",     message.getMsgSeqNum() != null ? message.getMsgSeqNum() : 0,
                "direction",     message.getDirection(),
                "receivedAt",    message.getReceivedAt().toString()
            );

            messaging.convertAndSend("/topic/messages", payload);

            // Also publish to session-specific topic
            String sessionTopic = "/topic/sessions/" + sanitizeTopic(message.getSessionId());
            messaging.convertAndSend(sessionTopic, payload);

        } catch (Exception e) {
            log.warn("Failed to publish live message to WebSocket: {}", e.getMessage());
        }
    }

    public void publishSessionStatus(String sessionId, String status) {
        try {
            messaging.convertAndSend("/topic/session-status",
                Map.of("sessionId", sessionId, "status", status));
        } catch (Exception e) {
            log.warn("Failed to publish session status update: {}", e.getMessage());
        }
    }

    private String sanitizeTopic(String sessionId) {
        return sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
