package com.loopers.domain.order;

import com.loopers.support.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code OrderRepositoryImpl}이 구현한다.
 * CAS(Compare-And-Set) 기반 상태 전이 메서드를 포함하여 동시성 안전한 주문 상태 변경을 지원한다.
 * </p>
 */
public interface OrderRepository {

    /**
     * 주문을 저장한다.
     *
     * @param order 저장할 주문 엔티티
     * @return 저장된 주문 엔티티
     */
    OrderModel save(OrderModel order);

    /**
     * 주문 ID로 주문을 조회한다.
     *
     * @param orderId 주문 ID
     * @return 주문 엔티티 (존재하지 않으면 빈 Optional)
     */
    Optional<OrderModel> findById(Long orderId);

    /**
     * 주문 ID와 사용자 ID로 주문을 조회한다.
     *
     * @param orderId 주문 ID
     * @param userId  사용자 ID
     * @return 주문 엔티티 (존재하지 않으면 빈 Optional)
     */
    Optional<OrderModel> findByIdAndUserId(Long orderId, Long userId);

    /**
     * 특정 사용자의 기간별 주문 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @param start  조회 시작 일시
     * @param end    조회 종료 일시
     * @return 해당 기간의 주문 목록
     */
    List<OrderModel> findAllByUserIdAndPeriod(Long userId, LocalDateTime start, LocalDateTime end);

    /**
     * 기간별 전체 주문 목록을 조회한다 (관리자용).
     *
     * @param start 조회 시작 일시
     * @param end   조회 종료 일시
     * @return 해당 기간의 전체 주문 목록
     */
    List<OrderModel> findAllByPeriod(LocalDateTime start, LocalDateTime end);

    /**
     * CAS(Compare-And-Set) 방식으로 주문 상태를 원자적으로 변경한다.
     * <p>
     * {@code UPDATE orders SET status = :to WHERE order_id = :orderId AND status = :from} 형태로
     * 현재 상태가 기대 상태({@code from})인 경우에만 변경이 수행된다.
     * 동시에 여러 스레드가 상태 변경을 시도하더라도 단 1개만 성공하여 경쟁 조건을 방지한다.
     * </p>
     *
     * @param orderId 주문 ID
     * @param from    변경 전 기대 상태
     * @param to      변경 후 상태
     * @return 영향받은 행 수 (0이면 상태 변경 실패 -- 이미 다른 상태로 전이됨)
     */
    int casUpdateStatus(Long orderId, OrderStatus from, OrderStatus to);

    /**
     * 특정 사용자의 특정 상태 주문 건수를 조회한다.
     *
     * @param userId 사용자 ID
     * @param status 주문 상태
     * @return 해당 상태의 주문 건수
     */
    long countByUserIdAndStatus(Long userId, OrderStatus status);

    /**
     * 만료 시간이 지난 결제 대기(PENDING_PAYMENT) 주문 목록을 조회한다.
     * 배치 스케줄러에서 만료 처리 대상을 조회할 때 사용한다.
     *
     * @return 만료 대상 주문 목록
     */
    List<OrderModel> findExpiredPendingOrders();
}
