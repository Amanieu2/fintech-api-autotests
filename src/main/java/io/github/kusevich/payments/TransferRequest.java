package io.github.kusevich.payments;

import java.math.BigDecimal;

public record TransferRequest(
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        String reference
) {
}
