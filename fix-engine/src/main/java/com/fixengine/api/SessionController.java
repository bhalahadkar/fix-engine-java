package com.fixengine.api;

import com.fixengine.dto.ApiResponse;
import com.fixengine.dto.SessionDto;
import com.fixengine.entity.FixSession;
import com.fixengine.service.FixSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "FIX Sessions", description = "Manage FIX sessions — create, configure, start, stop, reset")
public class SessionController {

    private final FixSessionService sessionService;

    @GetMapping
    @Operation(summary = "List all FIX sessions with live status")
    public ResponseEntity<ApiResponse<List<SessionDto>>> listSessions() {
        List<SessionDto> sessions = sessionService.getAllSessions()
                .stream()
                .map(sessionService::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(sessions));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get a single session")
    public ResponseEntity<ApiResponse<SessionDto>> getSession(@PathVariable String sessionId) {
        FixSession session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(sessionService.toDto(session)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new FIX session")
    public ResponseEntity<ApiResponse<FixSession>> createSession(@Valid @RequestBody FixSession session) {
        FixSession created = sessionService.createSession(session);
        return ResponseEntity.ok(ApiResponse.ok(created, "Session created"));
    }

    @PutMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Update session configuration (host, port, mode, etc.)")
    public ResponseEntity<ApiResponse<FixSession>> updateSession(
            @PathVariable String sessionId,
            @Valid @RequestBody FixSession updated) {
        FixSession saved = sessionService.updateSession(sessionId, updated);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Session updated"));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a FIX session")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Session deleted"));
    }

    @PostMapping("/{sessionId}/start")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Start / reconnect a FIX session")
    public ResponseEntity<ApiResponse<Void>> startSession(@PathVariable String sessionId) {
        log.info("GUI: Start session requested for {}", sessionId);
        sessionService.startSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Session start initiated"));
    }

    @PostMapping("/{sessionId}/stop")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Stop a FIX session (sends Logout)")
    public ResponseEntity<ApiResponse<Void>> stopSession(@PathVariable String sessionId) {
        log.info("GUI: Stop session requested for {}", sessionId);
        sessionService.stopSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Session stopped"));
    }

    @PostMapping("/{sessionId}/reset")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Reset sequence numbers to 1 and re-establish the session")
    public ResponseEntity<ApiResponse<Void>> resetSession(@PathVariable String sessionId) {
        log.info("GUI: Reset session requested for {}", sessionId);
        sessionService.resetSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Session reset to SeqNum 1"));
    }
}
