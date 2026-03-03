package com.fixengine.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class NodeInfoDto implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String nodeId;
    private String host;
    private int port;
    private boolean leader;
    private String status;
    private double cpuPercent;
    private double memPercent;
    private int activeSessions;
    private Instant lastHeartbeat;
}
