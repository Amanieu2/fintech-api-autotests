package io.github.kusevich.payments;

import java.math.BigDecimal;

public record TransferResponse(
        String transferId,
        String status,
        BigDecimal amount,
        String currency
) {
}
