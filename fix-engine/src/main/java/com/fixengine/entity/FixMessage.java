package com.fixengine.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@Table(name = "fix_messages", indexes = {
    @Index(name = "idx_fix_msg_session",  columnList = "session_id"),
    @Index(name = "idx_fix_msg_type",     columnList = "msg_type"),
    @Index(name = "idx_fix_msg_time",     columnList = "received_at DESC"),
    @Index(name = "idx_fix_msg_sender",   columnList = "sender_comp_id"),
    @Index(name = "idx_fix_msg_target",   columnList = "target_comp_id"),
    @Index(name = "idx_fix_msg_seqnum",   columnList = "session_id, direction, msg_seq_num")
})
public class FixMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fix_msg_seq")
    @SequenceGenerator(name = "fix_msg_seq", sequenceName = "fix_messages_seq", allocationSize = 50)
    private Long dbId;

    @Column(name = "message_id", unique = true, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, length = 200)
    private String sessionId;

    @Column(name = "sender_comp_id", nullable = false, length = 50)
    private String senderCompId;

    @Column(name = "target_comp_id", nullable = false, length = 50)
    private String targetCompId;

    @Column(name = "begin_string", length = 10)
    private String beginString;

    @Column(name = "msg_type", nullable = false, length = 10)
    private String msgType;

    @Column(name = "msg_seq_num")
    private Long msgSeqNum;

    /**
     * I = Inbound, O = Outbound
     */
    @Column(name = "direction", nullable = false, length = 1)
    private String direction;

    @Column(name = "raw_message", nullable = false, columnDefinition = "TEXT")
    private String rawMessage;

    @Column(name = "parsed_fields", columnDefinition = "TEXT")
    private String parsedFields;

    @Column(name = "sending_time")
    private Instant sendingTime;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "node_id", length = 50)
    private String nodeId;
}
