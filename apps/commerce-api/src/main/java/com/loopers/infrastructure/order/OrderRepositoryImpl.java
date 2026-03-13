package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 도메인 {@link OrderRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link OrderJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    /**
     * 주문을 저장한다.
     *
     * @param order 저장할 주문 엔티티
     * @return 저장된 주문 엔티티 (ID가 자동 생성됨)
     */
    @Override
    public OrderModel save(OrderModel order) {
        return jpaRepository.save(order);
    }

    /**
     * 주문 ID로 주문을 조회한다.
     *
     * @param orderId 주문 ID
     * @return 주문 (Optional)
     */
    @Override
    public Optional<OrderModel> findById(Long orderId) {
        return jpaRepository.findById(orderId);
    }

    /**
     * 주문 ID와 사용자 ID로 주문을 조회한다.
     *
     * @param orderId 주문 ID
     * @param userId  사용자 ID
     * @return 주문 (Optional)
     */
    @Override
    public Optional<OrderModel> findByIdAndUserId(Long orderId, Long userId) {
        return jpaRepository.findByOrderIdAndUserId(orderId, userId);
    }

    /**
     * 사용자 ID와 기간으로 주문 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @param start  조회 시작 일시
     * @param end    조회 종료 일시
     * @return 기간 내 해당 사용자의 주문 목록
     */
    @Override
    public List<OrderModel> findAllByUserIdAndPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findAllByUserIdAndPeriod(userId, start, end);
    }

    /**
     * 기간으로 전체 주문 목록을 조회한다.
     *
     * @param start 조회 시작 일시
     * @param end   조회 종료 일시
     * @return 기간 내 전체 주문 목록
     */
    @Override
    public List<OrderModel> findAllByPeriod(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findAllByPeriod(start, end);
    }

    /**
     * CAS(Compare-And-Set) 방식으로 주문 상태를 변경한다.
     *
     * <p>현재 상태가 {@code from}인 경우에만 {@code to}로 변경하여
     * 동시성 경쟁 조건을 방지한다.</p>
     *
     * @param orderId 주문 ID
     * @param from    변경 전 상태 (조건)
     * @param to      변경 후 상태
     * @return 변경된 행 수 (0이면 상태 전이 실패)
     */
    @Override
    public int casUpdateStatus(Long orderId, OrderStatus from, OrderStatus to) {
        return jpaRepository.casUpdateStatus(orderId, from, to);
    }

    /**
     * 사용자 ID와 주문 상태로 주문 건수를 조회한다.
     *
     * @param userId 사용자 ID
     * @param status 주문 상태
     * @return 조건에 해당하는 주문 건수
     */
    @Override
    public long countByUserIdAndStatus(Long userId, OrderStatus status) {
        return jpaRepository.countByUserIdAndStatus(userId, status);
    }

    /**
     * 만료 시각이 지난 결제 대기 주문 목록을 조회한다.
     *
     * @return 만료 대상 결제 대기 주문 목록
     */
    @Override
    public List<OrderModel> findExpiredPendingOrders() {
        return jpaRepository.findExpiredPendingOrders();
    }
}
