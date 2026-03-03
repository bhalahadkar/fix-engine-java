package com.fixengine.dto;

import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
public class ParsedMessageDto {
    private UUID messageId;
    private String sessionId;
    private String senderCompId;
    private String targetCompId;
    private String msgType;
    private String msgTypeName;
    private Long msgSeqNum;
    private String direction;
    private Instant sendingTime;
    private Instant receivedAt;
    private String rawMessage;
    private Map<Integer, String> tags;
}
