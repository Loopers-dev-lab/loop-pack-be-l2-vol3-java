package com.loopers.domain.order;

/**
 * 주문 장바구니 복원 기록 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code OrderCartRestoreRepositoryImpl}이 구현한다.
 * </p>
 */
public interface OrderCartRestoreRepository {

    /**
     * 장바구니 복원 기록을 저장한다.
     * <p>
     * PK가 orderId이므로 동일 주문에 대한 중복 저장 시
     * {@link org.springframework.dao.DataIntegrityViolationException}이 발생하여 멱등성을 보장한다.
     * </p>
     *
     * @param restore 저장할 복원 기록 엔티티
     * @return 저장된 복원 기록 엔티티
     */
    OrderCartRestoreModel save(OrderCartRestoreModel restore);

    /**
     * 주문 ID에 해당하는 장바구니 복원 기록이 존재하는지 확인한다.
     *
     * @param orderId 주문 ID
     * @return 존재 여부
     */
    boolean existsById(String orderId);
}
