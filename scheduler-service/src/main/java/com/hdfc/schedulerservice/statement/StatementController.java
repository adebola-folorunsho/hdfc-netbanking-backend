package com.hdfc.schedulerservice.statement;

import com.hdfc.schedulerservice.statement.dto.StatementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing statement query endpoints.
 *
 * <p>All endpoints are admin-only — routed exclusively through
 * Admin Gateway (port 8090) which enforces ADMIN role at the
 * gateway level. This service itself does not enforce security —
 * it trusts the gateway.</p>
 *
 * <p>All endpoints prefixed with /api/v1/scheduler per the
 * platform-wide REST versioning convention. Admin Gateway maps
 * /api/v1/admin/scheduler/** → scheduler-service /api/v1/scheduler/**</p>
 *
 * <p>SRP: this controller only receives HTTP requests, delegates
 * to StatementService, and returns HTTP responses. No business
 * logic lives here.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;

    /**
     * Returns a single statement record by its ID.
     *
     * <p>Example: GET /api/v1/scheduler/statements/1</p>
     *
     * @param id the statement ID to look up
     * @return   200 OK with StatementResponse body,
     *           or 404 NOT FOUND if no statement exists with the given ID
     */
    @GetMapping("/statements/{id}")
    public ResponseEntity<StatementResponse> getStatementById(@PathVariable Long id) {
        log.info("Admin request — fetch statement by id: {}", id);

        StatementResponse response = statementService.getStatementById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a paginated list of statements for a given user.
     *
     * <p>Default page size: 20. Never returns unbounded lists —
     * a user accumulates one statement per account per month.</p>
     *
     * <p>Example: GET /api/v1/scheduler/statements/user/100?page=0&size=20</p>
     *
     * @param userId   the user ID to filter by
     * @param pageable pagination and sorting parameters
     * @return         200 OK with paginated StatementResponse body
     */
    @GetMapping("/statements/user/{userId}")
    public ResponseEntity<Page<StatementResponse>> getStatementsByUserId(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("Admin request — fetch statements for userId: {}", userId);

        Page<StatementResponse> response = statementService.getStatementsByUserId(userId, pageable);
        return ResponseEntity.ok(response);
    }
}