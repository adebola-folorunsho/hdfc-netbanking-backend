package com.hdfc.auditservice.auditlog;

import com.hdfc.auditservice.auditlog.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing audit log query endpoints.
 *
 * <p>All endpoints are admin-only — routed exclusively through
 * Admin Gateway (port 8090) which enforces ADMIN role at the
 * gateway level. This service itself does not enforce security —
 * it trusts the gateway.</p>
 *
 * <p>All endpoints prefixed with /api/v1/audit per the
 * platform-wide REST versioning convention. Admin Gateway maps
 * /api/v1/admin/audit/** → audit-service /api/v1/audit/**</p>
 *
 * <p>SRP: this controller only receives HTTP requests, delegates
 * to AuditLogService, and returns HTTP responses. No business
 * logic lives here.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Returns a paginated list of all audit log entries.
     *
     * <p>Default page size: 20. Never returns unbounded lists —
     * the audit table grows indefinitely over time.</p>
     *
     * <p>Example: GET /api/v1/audit/logs?page=0&size=20</p>
     *
     * @param pageable pagination and sorting parameters
     * @return         200 OK with paginated AuditLogResponse body
     */
    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLogResponse>> getAllAuditLogs(
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("Admin request — fetch all audit logs, page: {}", pageable.getPageNumber());

        Page<AuditLogResponse> response = auditLogService.getAllAuditLogs(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a single audit log entry by its ID.
     *
     * <p>Example: GET /api/v1/audit/logs/1</p>
     *
     * @param id the audit log ID to look up
     * @return   200 OK with AuditLogResponse body,
     *           or 404 NOT FOUND if no audit log exists with the given ID
     */
    @GetMapping("/logs/{id}")
    public ResponseEntity<AuditLogResponse> getAuditLogById(@PathVariable Long id) {
        log.info("Admin request — fetch audit log by id: {}", id);

        AuditLogResponse response = auditLogService.getAuditLogById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a paginated list of audit logs filtered by event type.
     *
     * <p>Example: GET /api/v1/audit/logs/type/TRANSACTION_CREATED?page=0&size=20</p>
     *
     * @param eventType the event type to filter by e.g. "TRANSACTION_CREATED"
     * @param pageable  pagination and sorting parameters
     * @return          200 OK with paginated AuditLogResponse body
     */
    @GetMapping("/logs/type/{eventType}")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByEventType(
            @PathVariable String eventType,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("Admin request — fetch audit logs by eventType: {}", eventType);

        Page<AuditLogResponse> response = auditLogService.getAuditLogsByEventType(eventType, pageable);
        return ResponseEntity.ok(response);
    }
}