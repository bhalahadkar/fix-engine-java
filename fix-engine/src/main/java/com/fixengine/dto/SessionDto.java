package com.fixengine.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SessionDto {
    private String sessionId;
    private String beginString;
    private String senderCompId;
    private String targetCompId;
    private String mode;
    private String host;
    private Integer port;
    private Integer heartbeatInterval;
    private Boolean enabled;
    private String status;
    private Integer nextSenderSeq;
    private Integer nextTargetSeq;
    private Instant lastUpdated;
}
