package com.fixengine.service;

import com.fixengine.dto.MessageSearchRequest;
import com.fixengine.dto.ParsedMessageDto;
import com.fixengine.entity.FixMessage;
import com.fixengine.repository.FixMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixMessageService {

    private final FixMessageRepository messageRepository;

    public Page<FixMessage> searchMessages(MessageSearchRequest req, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("receivedAt").descending());
        return messageRepository.searchMessages(
                req.getSessionId(),
                req.getMsgType(),
                req.getDirection(),
                req.getSenderCompId(),
                req.getTargetCompId(),
                req.getFromTime(),
                req.getToTime(),
                req.getSearch(),
                pageable
        );
    }

    public Optional<FixMessage> getMessage(UUID messageId) {
        return messageRepository.findById(messageId);
    }

    public ParsedMessageDto parseMessage(FixMessage message) {
        ParsedMessageDto dto = new ParsedMessageDto();
        dto.setMessageId(message.getId());
        dto.setSessionId(message.getSessionId());
        dto.setSenderCompId(message.getSenderCompId());
        dto.setTargetCompId(message.getTargetCompId());
        dto.setMsgType(message.getMsgType());
        dto.setMsgTypeName(getMsgTypeName(message.getMsgType()));
        dto.setMsgSeqNum(message.getMsgSeqNum());
        dto.setDirection(message.getDirection());
        dto.setSendingTime(message.getSendingTime());
        dto.setReceivedAt(message.getReceivedAt());
        dto.setRawMessage(message.getRawMessage());
        dto.setTags(parseRawToTags(message.getRawMessage()));
        return dto;
    }

    /**
     * Parse raw FIX string (SOH-delimited or pipe-delimited) into tag→value map.
     */
    private Map<Integer, String> parseRawToTags(String raw) {
        Map<Integer, String> tags = new LinkedHashMap<>();
        if (raw == null) return tags;
        // FIX messages use SOH (0x01) as delimiter; log files often use pipe
        String delimiter = raw.contains("\u0001") ? "\u0001" : "\\|";
        String[] parts = raw.split(delimiter);
        for (String part : parts) {
            if (part.isBlank()) continue;
            int eq = part.indexOf('=');
            if (eq > 0) {
                try {
                    int tag = Integer.parseInt(part.substring(0, eq).trim());
                    String val = part.substring(eq + 1).trim();
                    tags.put(tag, val);
                } catch (NumberFormatException ignored) {}
            }
        }
        return tags;
    }

    private String getMsgTypeName(String msgType) {
        return switch (msgType) {
            case "D"  -> "NewOrderSingle";
            case "8"  -> "ExecutionReport";
            case "G"  -> "OrderCancelReplaceRequest";
            case "F"  -> "OrderCancelRequest";
            case "V"  -> "MarketDataRequest";
            case "W"  -> "MarketDataSnapshotFullRefresh";
            case "X"  -> "MarketDataIncrementalRefresh";
            case "0"  -> "Heartbeat";
            case "A"  -> "Logon";
            case "5"  -> "Logout";
            case "1"  -> "TestRequest";
            case "2"  -> "ResendRequest";
            case "3"  -> "Reject";
            case "4"  -> "SequenceReset";
            case "j"  -> "BusinessMessageReject";
            case "AE" -> "TradeCaptureReport";
            case "AK" -> "Confirmation";
            default   -> "Unknown(" + msgType + ")";
        };
    }
}
