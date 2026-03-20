package com.loopers.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * PG 라우터 — Primary PG 실패 시 Fallback PG로 전환하는 Strategy 기반 라우팅.
 *
 * <p>Phase 1에서는 기본 Fallback만 구현한다. CB 기반 Fallback은 Phase 2에서 추가.</p>
 *
 * <p>타임아웃 실패 시에는 Fallback PG로 전환하지 않는다 (중복 결제 방지, 05 §8.3 규칙).</p>
 */
@Slf4j
public class PgRouter {

    private final List<PgClient> pgClients;

    public PgRouter(List<PgClient> pgClients) {
        if (pgClients == null || pgClients.isEmpty()) {
            throw new IllegalArgumentException("PG 클라이언트가 최소 1개 이상 필요합니다.");
        }
        this.pgClients = pgClients;
    }

    /**
     * Primary PG로 결제 요청을 시도하고, 실패 시 Fallback PG로 전환한다.
     */
    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        Exception lastException = null;

        for (PgClient pgClient : pgClients) {
            try {
                PgPaymentResponse response = pgClient.requestPayment(request);
                log.info("PG 결제 요청 성공: provider={}, transactionKey={}",
                    pgClient.getProviderName(), response.transactionKey());
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("PG 결제 요청 실패: provider={}, error={}",
                    pgClient.getProviderName(), e.getMessage());
            }
        }

        throw new CoreException(ErrorType.INTERNAL_ERROR,
            "모든 PG 결제 요청이 실패했습니다. lastError=" +
                (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * 결제 상태 조회 — Primary PG에서만 조회한다 (transactionKey는 특정 PG에 종속).
     */
    public PgPaymentStatusResponse getPaymentStatus(String transactionKey, String pgProvider) {
        PgClient pgClient = findByProvider(pgProvider);
        return pgClient.getPaymentStatus(transactionKey);
    }

    /**
     * orderId 기반 결제 상태 조회 — Primary PG에서만 조회한다.
     */
    public PgPaymentStatusResponse getPaymentByOrderId(String orderId, String pgProvider) {
        PgClient pgClient = findByProvider(pgProvider);
        return pgClient.getPaymentByOrderId(orderId);
    }

    public PgClient getPrimaryClient() {
        return pgClients.get(0);
    }

    private PgClient findByProvider(String pgProvider) {
        return pgClients.stream()
            .filter(c -> c.getProviderName().equals(pgProvider))
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR,
                "PG 제공자를 찾을 수 없습니다: " + pgProvider));
    }
}
