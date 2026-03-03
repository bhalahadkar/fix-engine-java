package com.fixengine.api;

import com.fixengine.dto.ApiResponse;
import com.fixengine.dto.MessageSearchRequest;
import com.fixengine.dto.ParsedMessageDto;
import com.fixengine.entity.FixMessage;
import com.fixengine.service.FixMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "FIX Messages", description = "Search and inspect logged FIX messages")
public class MessageController {

    private final FixMessageService messageService;

    @GetMapping
    @Operation(summary = "Search messages with filter and pagination")
    public ResponseEntity<ApiResponse<Page<FixMessage>>> searchMessages(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String senderCompId,
            @RequestParam(required = false) String targetCompId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        MessageSearchRequest req = new MessageSearchRequest();
        req.setSessionId(sessionId);
        req.setMsgType(msgType);
        req.setDirection(direction);
        req.setSenderCompId(senderCompId);
        req.setTargetCompId(targetCompId);
        req.setSearch(search);
        if (fromTime != null) req.setFromTime(java.time.Instant.parse(fromTime));
        if (toTime   != null) req.setToTime(java.time.Instant.parse(toTime));

        Page<FixMessage> result = messageService.searchMessages(req, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{messageId}")
    @Operation(summary = "Get a single message with parsed tags")
    public ResponseEntity<ApiResponse<ParsedMessageDto>> getMessage(@PathVariable UUID messageId) {
        FixMessage msg = messageService.getMessage(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        ParsedMessageDto parsed = messageService.parseMessage(msg);
        return ResponseEntity.ok(ApiResponse.ok(parsed));
    }
}
