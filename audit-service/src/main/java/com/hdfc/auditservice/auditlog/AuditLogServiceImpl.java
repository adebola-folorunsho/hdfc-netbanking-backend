package com.hdfc.auditservice.auditlog;

import com.hdfc.auditservice.auditlog.dto.AuditLogResponse;
import com.hdfc.auditservice.auditlog.exception.AuditLogNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Implementation of AuditLogService.
 *
 * <p>SRP: this class is solely responsible for coordinating audit log
 * persistence and retrieval. It does not handle Kafka deserialization,
 * HTTP concerns, or entity mapping — those belong to their own classes.</p>
 *
 * <p>All write operations are @Transactional — audit records must be
 * persisted atomically. A partial write is worse than no write in an
 * audit system.</p>
 *
 * <p>All read operations are @Transactional(readOnly = true) —
 * readOnly hint allows Hibernate to skip dirty checking on reads,
 * improving performance on a potentially large audit table.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * {@inheritDoc}
     *
     * <p>Validates all inputs at the entry point — fail fast before
     * any DB interaction. Builds and persists the AuditLog entity.
     * createdAt is set automatically via @PrePersist on the entity.</p>
     */
    @Override
    @Transactional
    public void recordAuditLog(String eventType, String actor, String description) {
        validateField(eventType, "eventType");
        validateField(actor, "actor");
        validateField(description, "description");

        AuditLog auditLog = buildAuditLog(eventType, actor, description);
        auditLogRepository.save(auditLog);

        log.info("Audit log recorded — eventType: {}, actor: {}", eventType, actor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAllAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(Long id) {
        Objects.requireNonNull(id, "id must not be null");

        return auditLogRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new AuditLogNotFoundException(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByEventType(String eventType, Pageable pageable) {
        validateField(eventType, "eventType");

        return auditLogRepository.findByEventType(eventType, pageable)
                .map(this::mapToResponse);
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers — each does exactly one thing (SRP at method level)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Validates that a field value is non-null and non-blank.
     * Fails fast at the entry point — never propagates invalid state.
     */
    private void validateField(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    /**
     * Builds a new AuditLog entity from the given parameters.
     * createdAt is intentionally not set here — @PrePersist handles it.
     */
    private AuditLog buildAuditLog(String eventType, String actor, String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventType(eventType);
        auditLog.setActor(actor);
        auditLog.setDescription(description);
        return auditLog;
    }

    /**
     * Maps an AuditLog entity to an AuditLogResponse DTO.
     * Never exposes the JPA entity outside the service layer.
     */
    private AuditLogResponse mapToResponse(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .eventType(auditLog.getEventType())
                .actor(auditLog.getActor())
                .description(auditLog.getDescription())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}