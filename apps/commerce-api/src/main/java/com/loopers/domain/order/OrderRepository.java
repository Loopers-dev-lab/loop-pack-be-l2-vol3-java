package com.loopers.domain.order;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 주문 도메인 리포지토리 인터페이스.
 */
public interface OrderRepository {

    /**
     * 주문을 저장한다.
     *
     * @param order 저장할 주문
     * @return 저장된 주문
     */
    Order save(Order order);

    /**
     * ID로 주문을 주문 항목과 함께 조회한다.
     *
     * <p>주문 항목({@link OrderItem})을 페치 조인하여 함께 로딩한다.</p>
     *
     * @param orderId 주문 ID
     * @return 주문 항목을 포함한 주문 (존재하지 않으면 빈 Optional)
     */
    Optional<Order> findByIdWithItems(Long orderId);

    /**
     * 주문 키로 주문을 주문 항목과 함께 조회한다.
     *
     * <p>주문 항목({@link OrderItem})을 페치 조인하여 함께 로딩한다.</p>
     *
     * @param orderKey 주문 키
     * @return 주문 항목을 포함한 주문 (존재하지 않으면 빈 Optional)
     */
    Optional<Order> findByOrderKeyWithItems(String orderKey);

    /**
     * 전체 주문을 페이징 조회한다.
     *
     * @param pageable 페이징 조건
     * @return 주문 슬라이스
     */
    Slice<Order> findAll(Pageable pageable);

    /**
     * 특정 사용자의 주문을 기간 조건으로 페이징 조회한다.
     *
     * <p>주문일시가 start 이상 end 미만인 주문을 조회한다.</p>
     *
     * @param userId   사용자 ID
     * @param start    조회 시작 일시 (포함)
     * @param end      조회 종료 일시 (미포함)
     * @param pageable 페이징 조건
     * @return 주문 슬라이스
     */
    Slice<Order> findAllByUserIdAndOrderedAtBetween(Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
