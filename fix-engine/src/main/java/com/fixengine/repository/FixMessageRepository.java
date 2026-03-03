package com.fixengine.repository;

import com.fixengine.entity.FixMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FixMessageRepository extends JpaRepository<FixMessage, Long> {

    Optional<FixMessage> findById(UUID messageId);

    Page<FixMessage> findBySessionIdOrderByReceivedAtDesc(String sessionId, Pageable pageable);

    Page<FixMessage> findByMsgTypeOrderByReceivedAtDesc(String msgType, Pageable pageable);

    @Query("""
        SELECT m FROM FixMessage m
        WHERE (:sessionId   IS NULL OR m.sessionId   = :sessionId)
          AND (:msgType      IS NULL OR m.msgType     = :msgType)
          AND (:direction    IS NULL OR m.direction   = :direction)
          AND (:senderCompId IS NULL OR m.senderCompId = :senderCompId)
          AND (:targetCompId IS NULL OR m.targetCompId = :targetCompId)
          AND (:fromTime     IS NULL OR m.receivedAt  >= :fromTime)
          AND (:toTime       IS NULL OR m.receivedAt  <= :toTime)
          AND (:search IS NULL OR LOWER(m.rawMessage) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(m.parsedFields) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY m.receivedAt DESC
        """)
    Page<FixMessage> searchMessages(
            @Param("sessionId")   String sessionId,
            @Param("msgType")     String msgType,
            @Param("direction")   String direction,
            @Param("senderCompId") String senderCompId,
            @Param("targetCompId") String targetCompId,
            @Param("fromTime")    Instant fromTime,
            @Param("toTime")      Instant toTime,
            @Param("search")      String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(m) FROM FixMessage m WHERE m.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(m) FROM FixMessage m WHERE m.receivedAt >= :since")
    long countSince(@Param("since") Instant since);
}
