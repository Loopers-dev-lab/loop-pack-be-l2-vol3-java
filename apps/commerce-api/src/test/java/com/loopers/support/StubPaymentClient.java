package com.loopers.support;

import com.loopers.domain.payment.PaymentClient;
import com.loopers.domain.payment.PgApproveRequest;
import com.loopers.domain.payment.PgApproveResult;
import com.loopers.domain.payment.PgCancelResult;
import com.loopers.domain.payment.PgQueryResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 테스트용 PaymentClient Stub
 *
 * PG 시뮬레이터 없이 테스트를 돌리기 위해 항상 성공을 반환한다.
 * approve() → PENDING → query() → SUCCESS 흐름을 시뮬레이션한다.
 */
@Primary
@Component
public class StubPaymentClient implements PaymentClient {

    private String lastTransactionKey;

    @Override
    public PgApproveResult approve(PgApproveRequest request) {
        lastTransactionKey = "STUB-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return PgApproveResult.pending(lastTransactionKey);
    }

    @Override
    public PgQueryResult query(String transactionKey, Long userId) {
        String txnKey = transactionKey != null ? transactionKey : lastTransactionKey;
        return PgQueryResult.success(txnKey, "테스트 승인");
    }

    @Override
    public PgCancelResult cancel(String transactionKey, Long userId) {
        return PgCancelResult.ofSuccess();
    }
}
