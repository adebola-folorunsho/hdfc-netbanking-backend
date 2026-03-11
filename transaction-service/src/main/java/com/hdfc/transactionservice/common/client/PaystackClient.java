package com.hdfc.transactionservice.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdfc.transactionservice.common.exception.PaystackException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HTTP client for the Paystack payment gateway API.
 *
 * <p>Sandbox mode is determined by the key prefix in PAYSTACK_SECRET_KEY:
 * <ul>
 *   <li>sk_test_ — sandbox (used in development)</li>
 *   <li>sk_live_ — production</li>
 * </ul>
 *
 * <p>All monetary amounts sent to Paystack are in kobo (NGN subunit).
 * 1 NGN = 100 kobo. Conversion: multiply NGN amount by 100.
 *
 * <p>Paystack API reference: https://paystack.com/docs/api/
 */
@Component
@Slf4j
public class PaystackClient {

    private final WebClient webClient;
    private final String webhookSecret;

    public PaystackClient(
            @Value("${paystack.base-url}") String baseUrl,
            @Value("${paystack.secret-key}") String secretKey,
            @Value("${paystack.webhook-secret}") String webhookSecret,
            WebClient.Builder webClientBuilder) {

        this.webhookSecret = webhookSecret;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Initialises a Paystack payment and returns the authorization URL.
     *
     * <p>Paystack API: POST /transaction/initialize
     * Amount is converted from NGN to kobo before sending.
     *
     * @param email             the customer email address
     * @param amountNgn         the amount in NGN (not kobo)
     * @param reference         our internal transaction reference
     * @param description       optional payment description
     * @return a JsonNode containing authorizationUrl, accessCode,
     *         and Paystack reference
     * @throws PaystackException if Paystack returns an error response
     */
    public JsonNode initializePayment(
            String email,
            BigDecimal amountNgn,
            String reference,
            String description) {

        // Convert NGN to kobo: multiply by 100, no decimal places.
        long amountKobo = amountNgn
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_EVEN)
                .longValue();

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("amount", amountKobo);
        payload.put("reference", reference);
        if (description != null && !description.isBlank()) {
            payload.put("metadata", Map.of("description", description));
        }

        log.debug("Calling Paystack initialize: email={}, amountKobo={}, " +
                "reference={}", email, amountKobo, reference);

        try {
            JsonNode response = webClient.post()
                    .uri("/transaction/initialize")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("status").asBoolean()) {
                String message = response != null
                        ? response.path("message").asText("Unknown error")
                        : "Null response from Paystack";
                log.error("Paystack initialize failed: {}", message);
                throw new PaystackException(
                        "Paystack initialization failed: " + message);
            }

            log.debug("Paystack initialize success: reference={}", reference);
            return response.path("data");

        } catch (PaystackException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Paystack initialize error: {}", ex.getMessage(), ex);
            throw new PaystackException(
                    "Paystack API call failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Verifies a Paystack payment by reference.
     *
     * <p>Paystack API: GET /transaction/verify/{reference}
     * Called to confirm payment status after webhook or
     * for manual verification.
     *
     * @param paystackReference the Paystack-generated reference
     * @return a JsonNode containing the transaction status and details
     * @throws PaystackException if verification fails
     */
    public JsonNode verifyPayment(String paystackReference) {

        log.debug("Verifying Paystack payment: reference={}",
                paystackReference);

        try {
            JsonNode response = webClient.get()
                    .uri("/transaction/verify/{reference}", paystackReference)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("status").asBoolean()) {
                String message = response != null
                        ? response.path("message").asText("Unknown error")
                        : "Null response from Paystack";
                log.error("Paystack verify failed: {}", message);
                throw new PaystackException(
                        "Paystack verification failed: " + message);
            }

            log.debug("Paystack verify success: reference={}",
                    paystackReference);
            return response.path("data");

        } catch (PaystackException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Paystack verify error: {}", ex.getMessage(), ex);
            throw new PaystackException(
                    "Paystack verification failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Verifies the HMAC-SHA512 signature on a Paystack webhook request.
     *
     * <p>Paystack signs every webhook payload with HMAC-SHA512 using
     * the webhook secret as the key. The signature is sent in the
     * x-paystack-signature header. We compute the expected signature
     * and compare — if they do not match, the request is rejected.
     *
     * <p>This prevents malicious actors from sending fake webhook
     * events to mark transactions as completed without actual payment.
     *
     * @param payload   the raw request body bytes
     * @param signature the value of x-paystack-signature header
     * @return true if the signature is valid
     */
    public boolean isValidWebhookSignature(byte[] payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload);

            // Convert hash bytes to hex string for comparison.
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().equals(signature);

        } catch (Exception ex) {
            log.error("Webhook signature verification error: {}",
                    ex.getMessage(), ex);
            return false;
        }
    }
}