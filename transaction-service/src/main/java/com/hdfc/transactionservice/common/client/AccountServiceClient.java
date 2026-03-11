package com.hdfc.transactionservice.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdfc.transactionservice.common.exception.AccountServiceException;
import com.hdfc.transactionservice.common.exception.InsufficientBalanceException;
import com.hdfc.transactionservice.common.security.ServiceJwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HTTP client for calling Account Service REST endpoints.
 *
 * <p>All calls use the service-level JWT from {@link ServiceJwtProvider}
 * — never the user's JWT. This keeps Account Service's TELLER/ADMIN
 * restriction on debit/credit endpoints intact regardless of the
 * role of the user who initiated the transaction.
 *
 * <p>Service discovery: lb://account-service resolves via Eureka.
 * Spring Cloud LoadBalancer picks an available Account Service
 * instance on each call.
 *
 * <p>Error mapping:
 * <ul>
 *   <li>422 from Account Service → InsufficientBalanceException</li>
 *   <li>Any other 4xx/5xx → AccountServiceException</li>
 *   <li>Connection failure → AccountServiceException</li>
 * </ul>
 */
@Component
@Slf4j
public class AccountServiceClient {

    private final WebClient webClient;
    private final ServiceJwtProvider serviceJwtProvider;

    public AccountServiceClient(
            @Value("${account-service.base-url}") String baseUrl,
            WebClient.Builder webClientBuilder,
            ServiceJwtProvider serviceJwtProvider) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.serviceJwtProvider = serviceJwtProvider;
    }

    /**
     * Retrieves the balance of an account from Account Service.
     *
     * <p>GET /api/v1/accounts/{accountId}/balance
     *
     * @param accountId the account ID to check
     * @return JsonNode containing balance, currencyCode, and status
     * @throws AccountServiceException if the call fails
     */
    public JsonNode getAccountBalance(Long accountId) {
        log.debug("Fetching balance for accountId={}", accountId);
        try {
            JsonNode response = webClient.get()
                    .uri("/api/v1/accounts/{accountId}/balance", accountId)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceJwtProvider.getServiceToken())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                throw new AccountServiceException(
                        "Null response from Account Service for accountId="
                                + accountId);
            }

            return response.path("data");

        } catch (WebClientResponseException ex) {
            log.error("Account Service balance check failed: status={}, " +
                    "accountId={}", ex.getStatusCode(), accountId);
            throw new AccountServiceException(
                    "Account Service balance check failed for accountId="
                            + accountId + ": " + ex.getMessage(), ex);
        } catch (AccountServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Account Service balance check error: accountId={}, " +
                    "error={}", accountId, ex.getMessage(), ex);
            throw new AccountServiceException(
                    "Account Service unavailable: " + ex.getMessage(), ex);
        }
    }

    /**
     * Debits an account via Account Service.
     *
     * <p>POST /api/v1/accounts/{accountId}/debit
     *
     * @param accountId            the account to debit
     * @param amount               the amount to debit
     * @param currencyCode         the currency of the amount
     * @param transactionReference our reference for traceability
     * @throws InsufficientBalanceException if Account Service returns 422
     * @throws AccountServiceException      if the call fails for any other reason
     */
    public void debitAccount(
            Long accountId,
            BigDecimal amount,
            String currencyCode,
            String transactionReference) {

        log.debug("Debiting accountId={}, amount={} {}, ref={}",
                accountId, amount, currencyCode, transactionReference);

        Map<String, Object> payload = Map.of(
                "amount", amount,
                "currencyCode", currencyCode,
                "transactionReference", transactionReference
        );

        try {
            webClient.post()
                    .uri("/api/v1/accounts/{accountId}/debit", accountId)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceJwtProvider.getServiceToken())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            log.debug("Debit successful: accountId={}, ref={}",
                    accountId, transactionReference);

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                log.warn("Insufficient balance: accountId={}, amount={} {}",
                        accountId, amount, currencyCode);
                throw new InsufficientBalanceException(
                        "Insufficient balance in account " + accountId
                                + " for " + amount + " " + currencyCode);
            }
            log.error("Account Service debit failed: status={}, accountId={}",
                    ex.getStatusCode(), accountId);
            throw new AccountServiceException(
                    "Account Service debit failed for accountId="
                            + accountId + ": " + ex.getMessage(), ex);
        } catch (InsufficientBalanceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Account Service debit error: accountId={}, error={}",
                    accountId, ex.getMessage(), ex);
            throw new AccountServiceException(
                    "Account Service unavailable during debit: "
                            + ex.getMessage(), ex);
        }
    }

    /**
     * Credits an account via Account Service.
     *
     * <p>POST /api/v1/accounts/{accountId}/credit
     *
     * @param accountId            the account to credit
     * @param amount               the amount to credit
     * @param currencyCode         the currency of the amount
     * @param transactionReference our reference for traceability
     * @throws AccountServiceException if the call fails
     */
    public void creditAccount(
            Long accountId,
            BigDecimal amount,
            String currencyCode,
            String transactionReference) {

        log.debug("Crediting accountId={}, amount={} {}, ref={}",
                accountId, amount, currencyCode, transactionReference);

        Map<String, Object> payload = Map.of(
                "amount", amount,
                "currencyCode", currencyCode,
                "transactionReference", transactionReference
        );

        try {
            webClient.post()
                    .uri("/api/v1/accounts/{accountId}/credit", accountId)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceJwtProvider.getServiceToken())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            log.debug("Credit successful: accountId={}, ref={}",
                    accountId, transactionReference);

        } catch (WebClientResponseException ex) {
            log.error("Account Service credit failed: status={}, accountId={}",
                    ex.getStatusCode(), accountId);
            throw new AccountServiceException(
                    "Account Service credit failed for accountId="
                            + accountId + ": " + ex.getMessage(), ex);
        } catch (AccountServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Account Service credit error: accountId={}, error={}",
                    accountId, ex.getMessage(), ex);
            throw new AccountServiceException(
                    "Account Service unavailable during credit: "
                            + ex.getMessage(), ex);
        }
    }
}