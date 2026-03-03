package com.fixengine.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
@Table(name = "fix_sessions")
public class FixSession {

    @Id
    @Column(name = "session_id", length = 200)
    private String sessionId;

    @Column(name = "begin_string", nullable = false, length = 10)
    private String beginString;

    @Column(name = "sender_comp_id", nullable = false, length = 50)
    private String senderCompId;

    @Column(name = "target_comp_id", nullable = false, length = 50)
    private String targetCompId;

    /**
     * INITIATOR or ACCEPTOR
     */
    @Column(name = "mode", nullable = false, length = 10)
    private String mode;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "heartbeat_interval")
    private Integer heartbeatInterval = 30;

    @Column(name = "reconnect_interval")
    private Integer reconnectInterval = 10;

    @Column(name = "reset_on_logon")
    private Boolean resetOnLogon = false;

    @Column(name = "reset_on_logout")
    private Boolean resetOnLogout = false;

    @Column(name = "reset_on_disconnect")
    private Boolean resetOnDisconnect = false;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
