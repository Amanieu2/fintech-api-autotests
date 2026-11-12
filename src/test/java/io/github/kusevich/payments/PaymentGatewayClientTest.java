package io.github.kusevich.payments;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.github.kusevich.payments.PaymentGatewayClient.IDEMPOTENCY_HEADER;
import static io.github.kusevich.payments.PaymentGatewayClient.TRANSFERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("Payments")
@Feature("Bank payment gateway integration")
@Tag("payments")
@DisplayName("Payment gateway client (integration with WireMock)")
class PaymentGatewayClientTest {

    private static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @RegisterExtension
    static WireMockExtension gateway = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private PaymentGatewayClient client;

    @BeforeEach
    void setUp() {
        client = new PaymentGatewayClient(URI.create(gateway.baseUrl()), Duration.ofMillis(500), 3);
    }

    private static TransferRequest validTransfer() {
        return new TransferRequest("UZ0001", "UZ0002", new BigDecimal("1500.00"), "USD", "INV-42");
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Successful transfer: correct request body, headers and parsed response")
    void successfulTransfer() {
        gateway.stubFor(post(TRANSFERS_PATH)
                .withHeader(IDEMPOTENCY_HEADER, matching(UUID_REGEX))
                .withRequestBody(equalToJson("""
                        {"fromAccount":"UZ0001","toAccount":"UZ0002",
                         "amount":1500.00,"currency":"USD","reference":"INV-42"}"""))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"transferId":"TR-1001","status":"COMPLETED",
                                 "amount":1500.00,"currency":"USD","fee":0}""")));

        TransferResponse response = client.transfer(validTransfer());

        assertThat(response.transferId()).isEqualTo("TR-1001");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
        assertThat(response.currency()).isEqualTo("USD");
        gateway.verify(1, postRequestedFor(urlEqualTo(TRANSFERS_PATH))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description("A 503 is retried; the retry must reuse the same Idempotency-Key so the bank does not double-charge")
    @DisplayName("Retry after 503 reuses the same Idempotency-Key")
    void retryOn503KeepsIdempotencyKey() {
        gateway.stubFor(post(TRANSFERS_PATH).inScenario("flaky gateway")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        gateway.stubFor(post(TRANSFERS_PATH).inScenario("flaky gateway")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"transferId":"TR-1002","status":"COMPLETED","amount":1500.00,"currency":"USD"}""")));

        TransferResponse response = client.transfer(validTransfer());

        assertThat(response.transferId()).isEqualTo("TR-1002");
        List<LoggedRequest> requests = gateway.findAll(postRequestedFor(urlEqualTo(TRANSFERS_PATH)));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getHeader(IDEMPOTENCY_HEADER))
                .matches(UUID_REGEX)
                .isEqualTo(requests.get(1).getHeader(IDEMPOTENCY_HEADER));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Gateway down: stops after max attempts and reports GATEWAY_UNAVAILABLE")
    void gatewayDownExhaustsRetries() {
        gateway.stubFor(post(TRANSFERS_PATH).willReturn(aResponse().withStatus(502)));

        assertThatThrownBy(() -> client.transfer(validTransfer()))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getCode())
                .isEqualTo(PaymentException.ErrorCode.GATEWAY_UNAVAILABLE);
        gateway.verify(3, postRequestedFor(urlEqualTo(TRANSFERS_PATH)));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Slow gateway: client times out and retries, then reports TIMEOUT")
    void slowGatewayTimesOut() {
        gateway.stubFor(post(TRANSFERS_PATH).willReturn(aResponse()
                .withStatus(201)
                .withFixedDelay(1_500)
                .withBody("{}")));

        assertThatThrownBy(() -> client.transfer(validTransfer()))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getCode())
                .isEqualTo(PaymentException.ErrorCode.TIMEOUT);
        // delayed responses may still be in flight; give the journal a moment
        awaitRequestCount(3, Duration.ofSeconds(3));
    }

    private static void awaitRequestCount(int expected, Duration maxWait) {
        long deadline = System.nanoTime() + maxWait.toNanos();
        int actual;
        do {
            actual = gateway.findAll(postRequestedFor(urlEqualTo(TRANSFERS_PATH))).size();
            if (actual == expected) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (System.nanoTime() < deadline);
        assertThat(actual).as("requests received by gateway").isEqualTo(expected);
    }

    static Stream<Arguments> businessErrors() {
        return Stream.of(
                Arguments.of(422, "{\"code\":\"INSUFFICIENT_FUNDS\",\"message\":\"Not enough money\"}",
                        PaymentException.ErrorCode.INSUFFICIENT_FUNDS),
                Arguments.of(422, "{\"code\":\"ACCOUNT_BLOCKED\",\"message\":\"Account is blocked\"}",
                        PaymentException.ErrorCode.ACCOUNT_BLOCKED),
                Arguments.of(400, "{\"code\":\"BAD_IBAN\",\"message\":\"Invalid account\"}",
                        PaymentException.ErrorCode.VALIDATION_ERROR),
                Arguments.of(401, "Unauthorized", PaymentException.ErrorCode.UNAUTHORIZED),
                Arguments.of(409, "", PaymentException.ErrorCode.UNEXPECTED)
        );
    }

    @ParameterizedTest(name = "HTTP {0} -> {2}")
    @MethodSource("businessErrors")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("4xx errors are mapped to domain codes and never retried")
    void clientErrorsAreMappedAndNotRetried(int status, String body, PaymentException.ErrorCode expected) {
        gateway.stubFor(post(TRANSFERS_PATH).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        assertThatThrownBy(() -> client.transfer(validTransfer()))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getCode())
                .isEqualTo(expected);
        gateway.verify(1, postRequestedFor(urlEqualTo(TRANSFERS_PATH)));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("zero amount",
                        new TransferRequest("UZ0001", "UZ0002", BigDecimal.ZERO, "USD", null)),
                Arguments.of("negative amount",
                        new TransferRequest("UZ0001", "UZ0002", new BigDecimal("-10.00"), "USD", null)),
                Arguments.of("3 decimal places",
                        new TransferRequest("UZ0001", "UZ0002", new BigDecimal("10.005"), "USD", null)),
                Arguments.of("same account",
                        new TransferRequest("UZ0001", "UZ0001", BigDecimal.TEN, "USD", null)),
                Arguments.of("lowercase currency",
                        new TransferRequest("UZ0001", "UZ0002", BigDecimal.TEN, "usd", null)),
                Arguments.of("missing account",
                        new TransferRequest(" ", "UZ0002", BigDecimal.TEN, "USD", null))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Invalid requests are rejected locally without calling the gateway")
    void invalidRequestsNeverReachGateway(String caseName, TransferRequest request) {
        assertThatThrownBy(() -> client.transfer(request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getCode())
                .isEqualTo(PaymentException.ErrorCode.VALIDATION_ERROR);
        gateway.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Malformed 200 response is reported as UNEXPECTED, not swallowed")
    void malformedSuccessResponse() {
        gateway.stubFor(post(TRANSFERS_PATH).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("<html>proxy error</html>")));

        assertThatThrownBy(() -> client.transfer(validTransfer()))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getCode())
                .isEqualTo(PaymentException.ErrorCode.UNEXPECTED);
    }
}
