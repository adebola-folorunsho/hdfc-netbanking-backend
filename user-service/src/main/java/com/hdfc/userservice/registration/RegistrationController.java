package com.hdfc.userservice.registration;

import com.hdfc.userservice.common.response.ApiResponse;
import com.hdfc.userservice.registration.dto.UserRegistrationRequest;
import com.hdfc.userservice.registration.dto.UserRegistrationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user registration.
 *
 * <p>Exposes a single public endpoint for new user registration.
 * This controller's sole responsibility is to receive the HTTP request,
 * delegate to the service, and return the HTTP response. No business
 * logic lives here — satisfies SRP at the controller level.
 *
 * <p>Base path: {@code /api/v1/users}
 * This endpoint is permitted without authentication in
 * {@link com.hdfc.userservice.common.security.config.SecurityConfig}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class RegistrationController {

    // Depends on the interface — never the concrete implementation
    private final IRegistrationService registrationService;

    /**
     * Registers a new user in the HDFC NetBanking system.
     *
     * <p>The {@code @Valid} annotation triggers Bean Validation on the
     * request body before the method is called. If validation fails,
     * Spring throws {@link org.springframework.web.bind.MethodArgumentNotValidException}
     * which the {@link com.hdfc.userservice.common.exception.GlobalExceptionHandler}
     * catches and returns as a structured 400 response.
     *
     * <p>On success, returns HTTP 201 Created — not 200 OK.
     * 201 is the semantically correct status for resource creation.
     *
     * @param request the validated registration request from the client
     * @return 201 Created with the registered user's details,
     *         or 400/409/422 if validation or business rules fail
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> register(
            @Valid @RequestBody UserRegistrationRequest request) {

        log.info("Registration request received for email: {}",
                request.getEmail());

        UserRegistrationResponse response = registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "User registered successfully", response));
    }
}