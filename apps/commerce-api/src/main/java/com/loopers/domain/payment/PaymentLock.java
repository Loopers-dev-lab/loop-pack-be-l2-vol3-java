package com.loopers.domain.payment;

/**
 * 결제 분산락 인터페이스.
 * <p>
 * 동일 주문에 대한 동시 결제 요청을 차단하기 위한 도메인 계약.
 * infrastructure 계층에서 Redis 등으로 구현한다.
 * </p>
 */
public interface PaymentLock {

    /**
     * 결제 분산락 획득을 시도한다.
     *
     * @param orderId    주문 ID
     * @param lockContext 락 value (디버깅용)
     * @return true: 획득 성공, false: 이미 다른 결제 진행 중
     * @throws PaymentLockException Redis 등 인프라 장애 시
     */
    boolean tryLock(Long orderId, String lockContext);

    /**
     * 결제 분산락을 해제한다.
     * 본인이 획득한 락만 해제하며 (owner 검증), 해제 실패 시 TTL로 자동 만료되므로 예외를 삼킨다.
     *
     * @param orderId     주문 ID
     * @param lockContext 락 획득 시 사용한 value (owner 검증용)
     */
    void unlock(Long orderId, String lockContext);

    /**
     * 인프라 장애를 구분하기 위한 예외.
     * 호출부에서 인프라 장애(fallback)와 락 경합(즉시 거부)을 구분한다.
     */
    class PaymentLockException extends RuntimeException {
        public PaymentLockException(Throwable cause) {
            super("분산락 인프라 오류", cause);
        }
    }
}
