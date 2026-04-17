package com.loopers.fake;

import com.loopers.infrastructure.pg.*;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PgClient Fake 구현체. 성공/실패를 외부에서 제어할 수 있다.
 *
 * <p>Phase 2 확장: failCount 기반 카운트다운 실패, orderId 기반 상태 조회</p>
 * <p>Phase 6 확장: 동기 응답 상태 설정(Toss 시뮬레이션), 타임아웃 시뮬레이션</p>
 */
public class FakePgClient implements PgClient {

    private final String providerName;
    private boolean shouldFail;
    private String failMessage = "PG 요청 실패";
    private int callCount;
    private int failUntilCall;
    private String responseStatus = "PENDING";
    private boolean throwTimeout;
    private final Map<String, PgPaymentStatusResponse> statusStore = new ConcurrentHashMap<>();
    private final Map<String, PgPaymentStatusResponse> orderStatusStore = new ConcurrentHashMap<>();

    public FakePgClient(String providerName) {
        this.providerName = providerName;
    }

    public FakePgClient(String providerName, boolean shouldFail) {
        this.providerName = providerName;
        this.shouldFail = shouldFail;
    }

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    public void setFailMessage(String failMessage) {
        this.failMessage = failMessage;
    }

    /**
     * 응답 상태를 설정한다. Toss 동기 PG 시뮬레이션: "SUCCESS" 또는 "FAILED".
     */
    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    /**
     * 타임아웃 시뮬레이션 모드. true → SocketTimeoutException 발생.
     */
    public void setThrowTimeout(boolean throwTimeout) {
        this.throwTimeout = throwTimeout;
    }

    /**
     * 정확히 count번 실패 후 성공으로 전환한다.
     */
    public void setFailCount(int count) {
        this.failUntilCall = count;
    }

    /**
     * orderId 기반으로 PG 상태를 미리 등록한다 (멱등성 테스트용).
     */
    public void registerOrderStatus(String orderId, PgPaymentStatusResponse response) {
        orderStatusStore.put(orderId, response);
    }

    public int getCallCount() {
        return callCount;
    }

    public void registerStatus(String transactionKey, PgPaymentStatusResponse response) {
        statusStore.put(transactionKey, response);
    }

    @Override
    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        callCount++;

        if (throwTimeout) {
            throw new RuntimeException("Read timed out",
                new SocketTimeoutException("Read timed out"));
        }

        if (shouldFail || (failUntilCall > 0 && callCount <= failUntilCall)) {
            throw new RuntimeException(failMessage);
        }

        String transactionKey = "TX-" + UUID.randomUUID().toString().substring(0, 8);
        statusStore.put(transactionKey,
            new PgPaymentStatusResponse(responseStatus, transactionKey, null));
        return new PgPaymentResponse(responseStatus, transactionKey);
    }

    @Override
    public PgPaymentStatusResponse getPaymentStatus(String transactionKey) {
        PgPaymentStatusResponse response = statusStore.get(transactionKey);
        if (response == null) {
            throw new RuntimeException("PG에 해당 거래가 없습니다: " + transactionKey);
        }
        return response;
    }

    @Override
    public PgPaymentStatusResponse getPaymentByOrderId(String orderId) {
        PgPaymentStatusResponse orderStatus = orderStatusStore.get(orderId);
        if (orderStatus != null) return orderStatus;

        return statusStore.values().stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("PG에 해당 주문의 결제가 없습니다: " + orderId));
    }

    @Override
    public String getProviderName() {
        return providerName;
    }
}
