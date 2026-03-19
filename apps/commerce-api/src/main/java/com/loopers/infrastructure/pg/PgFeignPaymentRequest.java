package com.loopers.infrastructure.pg;

import java.math.BigDecimal;

public record PgFeignPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        String amount,
        String callbackUrl
) {
}
