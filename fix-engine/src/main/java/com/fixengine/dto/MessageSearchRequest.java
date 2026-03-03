package com.fixengine.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Data
public class MessageSearchRequest {
    private String sessionId;
    private String msgType;
    private String direction;       // I or O
    private String senderCompId;
    private String targetCompId;
    private String search;          // Free text search
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant fromTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant toTime;
}
