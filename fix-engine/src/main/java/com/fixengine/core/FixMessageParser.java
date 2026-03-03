package com.fixengine.core;

import com.fixengine.config.FixEngineProperties;
import com.fixengine.entity.FixMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.UUID;

/**
 * Parses QuickFIX/J Message objects into FixMessage JPA entities.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixMessageParser {

    private final FixEngineProperties properties;

    public FixMessage toEntity(Message message, SessionID sessionId, String direction) {
        FixMessage entity = new FixMessage();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId.toString());
        entity.setSenderCompId(sessionId.getSenderCompID());
        entity.setTargetCompId(sessionId.getTargetCompID());
        entity.setBeginString(sessionId.getBeginString());
        entity.setDirection(direction);
        entity.setNodeId(properties.getNode().getId());
        entity.setRawMessage(message.toString());
        entity.setReceivedAt(Instant.now());

        try {
            entity.setMsgType(message.getHeader().getString(MsgType.FIELD));
        } catch (FieldNotFound e) {
            entity.setMsgType("UNKNOWN");
        }

        try {
            entity.setMsgSeqNum((long) message.getHeader().getInt(MsgSeqNum.FIELD));
        } catch (FieldNotFound e) {
            entity.setMsgSeqNum(0L);
        }

        try {
            UtcTimeStamp sendingTime = new UtcTimeStamp();
            message.getHeader().getField(sendingTime);
            entity.setSendingTime(sendingTime.getValue().toInstant());
        } catch (FieldNotFound e) {
            entity.setSendingTime(Instant.now());
        }

        // Extract body fields as structured text for search
        entity.setParsedFields(extractParsedFields(message));

        return entity;
    }

    private String extractParsedFields(Message message) {
        StringBuilder sb = new StringBuilder();
        appendFields(sb, message.getHeader(), "HDR");
        appendFields(sb, message, "BODY");
        appendFields(sb, message.getTrailer(), "TRL");
        return sb.toString();
    }

    private void appendFields(StringBuilder sb, FieldMap fieldMap, String section) {
        Iterator<Field<?>> iter = fieldMap.iterator();
        while (iter.hasNext()) {
            Field<?> field = iter.next();
            sb.append(section).append(":").append(field.getTag())
              .append("=").append(field.getObject()).append("|");
        }
    }
}
