package com.loopers.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketTimeoutException;
import java.util.List;

/**
 * PG 라우터 — Primary PG 실패 시 Fallback PG로 전환하는 Strategy 기반 라우팅.
 *
 * <p>타임아웃 규칙 (05 §8.3):</p>
 * <ul>
 *   <li>SocketTimeoutException → Fallback 전환하지 않음 (PG가 요청을 수신했을 수 있음 → 중복 결제 방지)</li>
 *   <li>ConnectException, 500, CB Open → Fallback 전환 (PG에 도달하지 않음 = 안전)</li>
 * </ul>
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
     *
     * <p>응답에 pgProvider를 주입하여 어떤 PG가 처리했는지 추적한다.</p>
     *
     * @throws CoreException 타임아웃 시 (Fallback 전환 안 함) 또는 모든 PG 실패 시
     */
    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        Exception lastException = null;

        for (PgClient pgClient : pgClients) {
            try {
                PgPaymentResponse response = pgClient.requestPayment(request);
                log.info("PG 결제 요청 성공: provider={}, transactionKey={}",
                    pgClient.getProviderName(), response.transactionKey());
                return new PgPaymentResponse(
                    response.status(), response.transactionKey(), pgClient.getProviderName());
            } catch (Exception e) {
                // 타임아웃 → Fallback 전환하지 않음 (중복 결제 방지)
                if (isTimeoutException(e)) {
                    log.warn("PG 타임아웃 — Fallback 전환 안 함: provider={}, error={}",
                        pgClient.getProviderName(), e.getMessage());
                    throw new CoreException(ErrorType.INTERNAL_ERROR,
                        "PG 타임아웃: " + pgClient.getProviderName() +
                        " (Fallback 전환 불가 — 중복 결제 방지)");
                }

                lastException = e;
                log.warn("PG 결제 요청 실패 — 다음 PG 시도: provider={}, error={}",
                    pgClient.getProviderName(), e.getMessage());
            }
        }

        throw new CoreException(ErrorType.INTERNAL_ERROR,
            "모든 PG 결제 요청이 실패했습니다. lastError=" +
                (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * 결제 상태 조회 — transactionKey가 속한 PG에서만 조회한다.
     */
    public PgPaymentStatusResponse getPaymentStatus(String transactionKey, String pgProvider) {
        PgClient pgClient = findByProvider(pgProvider);
        return pgClient.getPaymentStatus(transactionKey);
    }

    /**
     * orderId 기반 결제 상태 조회 — 지정된 PG에서 조회한다.
     */
    public PgPaymentStatusResponse getPaymentByOrderId(String orderId, String pgProvider) {
        PgClient pgClient = findByProvider(pgProvider);
        return pgClient.getPaymentByOrderId(orderId);
    }

    public PgClient getPrimaryClient() {
        return pgClients.get(0);
    }

    /**
     * 타임아웃 예외 판별.
     * Feign은 SocketTimeoutException을 RetryableException으로 감싸므로 cause 체인을 탐색한다.
     */
    private boolean isTimeoutException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private PgClient findByProvider(String pgProvider) {
        return pgClients.stream()
            .filter(c -> c.getProviderName().equals(pgProvider))
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR,
                "PG 제공자를 찾을 수 없습니다: " + pgProvider));
    }
}
