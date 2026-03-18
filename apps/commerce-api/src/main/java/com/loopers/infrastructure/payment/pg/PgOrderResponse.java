package com.loopers.infrastructure.payment.pg;

import java.util.List;

public record PgOrderResponse(String orderId, List<PgTransactionResponse> transactions) {
}
