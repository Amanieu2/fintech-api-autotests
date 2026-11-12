package io.github.kusevich.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.UUID;

/**
 * Minimal client for an external bank payment gateway.
 * <p>
 * Behaviour under test:
 * <ul>
 *   <li>validates the request locally before calling the gateway;</li>
 *   <li>sends a unique {@code Idempotency-Key} and keeps it the same across retries;</li>
 *   <li>retries on 5xx and timeouts, never on 4xx business errors;</li>
 *   <li>maps gateway error codes to {@link PaymentException.ErrorCode}.</li>
 * </ul>
 */
public final class PaymentGatewayClient {

    public static final String TRANSFERS_PATH = "/v1/transfers";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final HttpClient http;
    private final URI baseUri;
    private final Duration timeout;
    private final int maxAttempts;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PaymentGatewayClient(URI baseUri, Duration timeout, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.baseUri = baseUri;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
    }

    public TransferResponse transfer(TransferRequest request) {
        validate(request);

        String idempotencyKey = UUID.randomUUID().toString();
        String body = toJson(request);
        PaymentException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve(TRANSFERS_PATH))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response;
            try {
                response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                lastError = new PaymentException(PaymentException.ErrorCode.TIMEOUT,
                        "Gateway timeout on attempt " + attempt);
                continue;
            } catch (IOException e) {
                lastError = new PaymentException(PaymentException.ErrorCode.NETWORK_ERROR,
                        "Network error on attempt " + attempt + ": " + e.getMessage());
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PaymentException(PaymentException.ErrorCode.NETWORK_ERROR, "Interrupted");
            }

            int status = response.statusCode();
            if (status == 200 || status == 201) {
                return fromJson(response.body());
            }
            if (status >= 500) {
                lastError = new PaymentException(PaymentException.ErrorCode.GATEWAY_UNAVAILABLE,
                        "Gateway returned HTTP " + status + " on attempt " + attempt);
                continue;
            }
            throw mapClientError(status, response.body());
        }
        throw lastError;
    }

    private static void validate(TransferRequest r) {
        if (r == null) {
            throw validation("Request must not be null");
        }
        if (isBlank(r.fromAccount()) || isBlank(r.toAccount())) {
            throw validation("Both accounts are required");
        }
        if (r.fromAccount().equals(r.toAccount())) {
            throw validation("Source and destination accounts must differ");
        }
        if (r.amount() == null || r.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw validation("Amount must be positive");
        }
        if (r.amount().scale() > 2) {
            throw validation("Amount must have at most 2 decimal places");
        }
        if (r.currency() == null || !r.currency().matches("[A-Z]{3}")) {
            throw validation("Currency must be an ISO 4217 code");
        }
    }

    private PaymentException mapClientError(int status, String body) {
        String code = null;
        String message = "HTTP " + status;
        try {
            JsonNode node = mapper.readTree(body);
            code = node.path("code").asText(null);
            message = node.path("message").asText(message);
        } catch (IOException ignored) {
            // non-JSON error body: fall back to status-based mapping
        }

        PaymentException.ErrorCode errorCode;
        if ("INSUFFICIENT_FUNDS".equals(code)) {
            errorCode = PaymentException.ErrorCode.INSUFFICIENT_FUNDS;
        } else if ("ACCOUNT_BLOCKED".equals(code)) {
            errorCode = PaymentException.ErrorCode.ACCOUNT_BLOCKED;
        } else if (status == 401 || status == 403) {
            errorCode = PaymentException.ErrorCode.UNAUTHORIZED;
        } else if (status == 400 || status == 422) {
            errorCode = PaymentException.ErrorCode.VALIDATION_ERROR;
        } else {
            errorCode = PaymentException.ErrorCode.UNEXPECTED;
        }
        return new PaymentException(errorCode, message);
    }

    private String toJson(TransferRequest request) {
        try {
            return mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new PaymentException(PaymentException.ErrorCode.UNEXPECTED, e.getMessage());
        }
    }

    private TransferResponse fromJson(String body) {
        try {
            return mapper.readValue(body, TransferResponse.class);
        } catch (JsonProcessingException e) {
            throw new PaymentException(PaymentException.ErrorCode.UNEXPECTED,
                    "Malformed gateway response: " + e.getOriginalMessage());
        }
    }

    private static PaymentException validation(String message) {
        return new PaymentException(PaymentException.ErrorCode.VALIDATION_ERROR, message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
