package io.github.kusevich.payments;

public class PaymentException extends RuntimeException {

    public enum ErrorCode {
        VALIDATION_ERROR,
        INSUFFICIENT_FUNDS,
        ACCOUNT_BLOCKED,
        UNAUTHORIZED,
        GATEWAY_UNAVAILABLE,
        TIMEOUT,
        NETWORK_ERROR,
        UNEXPECTED
    }

    private final ErrorCode code;

    public PaymentException(ErrorCode code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
