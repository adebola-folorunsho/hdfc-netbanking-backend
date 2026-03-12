package com.hdfc.auditservice.auditlog;

import com.hdfc.auditservice.auditlog.dto.AuditLogResponse;
import com.hdfc.auditservice.auditlog.exception.AuditLogNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService Unit Tests")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private static final Long AUDIT_LOG_ID = 1L;
    private static final String EVENT_TYPE = "TRANSACTION_CREATED";
    private static final String ACTOR = "user-service";
    private static final String DESCRIPTION = "Fund transfer of NGN 5000 from account 1 to account 2";

    // ─────────────────────────────────────────────────────────────────
    // recordAuditLog tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should persist audit log entry when valid event is received")
    void shouldPersistAuditLogEntry_whenValidEventIsReceived() {
        // Arrange
        AuditLog auditLog = buildAuditLog();
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(auditLog);

        // Act
        auditLogService.recordAuditLog(EVENT_TYPE, ACTOR, DESCRIPTION);

        // Assert — repository save must be called exactly once
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when eventType is blank")
    void shouldThrowIllegalArgumentException_whenEventTypeIsBlank() {
        assertThatThrownBy(() -> auditLogService.recordAuditLog("", ACTOR, DESCRIPTION))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when actor is blank")
    void shouldThrowIllegalArgumentException_whenActorIsBlank() {
        assertThatThrownBy(() -> auditLogService.recordAuditLog(EVENT_TYPE, "", DESCRIPTION))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when description is blank")
    void shouldThrowIllegalArgumentException_whenDescriptionIsBlank() {
        assertThatThrownBy(() -> auditLogService.recordAuditLog(EVENT_TYPE, ACTOR, ""))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(auditLogRepository);
    }

    // ─────────────────────────────────────────────────────────────────
    // getAuditLogById tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return audit log when it exists")
    void shouldReturnAuditLog_whenItExists() {
        // Arrange
        AuditLog auditLog = buildAuditLog();
        when(auditLogRepository.findById(AUDIT_LOG_ID)).thenReturn(Optional.of(auditLog));

        // Act
        AuditLogResponse response = auditLogService.getAuditLogById(AUDIT_LOG_ID);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(response.getActor()).isEqualTo(ACTOR);
        assertThat(response.getDescription()).isEqualTo(DESCRIPTION);

        verify(auditLogRepository, times(1)).findById(AUDIT_LOG_ID);
    }

    @Test
    @DisplayName("Should throw AuditLogNotFoundException when audit log does not exist")
    void shouldThrowAuditLogNotFoundException_whenAuditLogDoesNotExist() {
        // Arrange
        when(auditLogRepository.findById(AUDIT_LOG_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> auditLogService.getAuditLogById(AUDIT_LOG_ID))
                .isInstanceOf(AuditLogNotFoundException.class)
                .hasMessageContaining(String.valueOf(AUDIT_LOG_ID));

        verify(auditLogRepository, times(1)).findById(AUDIT_LOG_ID);
    }

    // ─────────────────────────────────────────────────────────────────
    // getAllAuditLogs tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return paginated audit logs")
    void shouldReturnPaginatedAuditLogs() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> auditLogPage = new PageImpl<>(List.of(buildAuditLog()));
        when(auditLogRepository.findAll(pageable)).thenReturn(auditLogPage);

        // Act
        Page<AuditLogResponse> result = auditLogService.getAllAuditLogs(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventType()).isEqualTo(EVENT_TYPE);

        verify(auditLogRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("Should return empty page when no audit logs exist")
    void shouldReturnEmptyPage_whenNoAuditLogsExist() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(pageable)).thenReturn(Page.empty());

        // Act
        Page<AuditLogResponse> result = auditLogService.getAllAuditLogs(pageable);

        // Assert — never return null, always return empty page
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();

        verify(auditLogRepository, times(1)).findAll(pageable);
    }

    // ─────────────────────────────────────────────────────────────────
    // getAuditLogsByEventType tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return audit logs filtered by event type")
    void shouldReturnAuditLogs_filteredByEventType() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> auditLogPage = new PageImpl<>(List.of(buildAuditLog()));
        when(auditLogRepository.findByEventType(EVENT_TYPE, pageable)).thenReturn(auditLogPage);

        // Act
        Page<AuditLogResponse> result = auditLogService.getAuditLogsByEventType(EVENT_TYPE, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventType()).isEqualTo(EVENT_TYPE);

        verify(auditLogRepository, times(1)).findByEventType(EVENT_TYPE, pageable);
    }

    // ─────────────────────────────────────────────────────────────────
    // Test data builder
    // ─────────────────────────────────────────────────────────────────

    private AuditLog buildAuditLog() {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(AUDIT_LOG_ID);
        auditLog.setEventType(EVENT_TYPE);
        auditLog.setActor(ACTOR);
        auditLog.setDescription(DESCRIPTION);
        auditLog.setCreatedAt(Instant.now());
        return auditLog;
    }
}