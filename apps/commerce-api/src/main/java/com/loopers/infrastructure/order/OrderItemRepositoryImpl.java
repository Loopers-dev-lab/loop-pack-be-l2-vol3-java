package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 도메인 {@link OrderItemRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link OrderItemJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final OrderItemJpaRepository jpaRepository;

    /**
     * 주문 항목을 저장한다.
     *
     * @param item 저장할 주문 항목 엔티티
     * @return 저장된 주문 항목 엔티티
     */
    @Override
    public OrderItemModel save(OrderItemModel item) {
        return jpaRepository.save(item);
    }

    /**
     * 여러 주문 항목을 일괄 저장한다.
     *
     * @param items 저장할 주문 항목 엔티티 목록
     * @return 저장된 주문 항목 엔티티 목록
     */
    @Override
    public List<OrderItemModel> saveAll(List<OrderItemModel> items) {
        return jpaRepository.saveAll(items);
    }

    /**
     * 주문 ID로 해당 주문의 주문 항목 목록을 조회한다.
     *
     * @param orderId 주문 ID
     * @return 해당 주문의 주문 항목 목록
     */
    @Override
    public List<OrderItemModel> findAllByOrderId(String orderId) {
        return jpaRepository.findAllByOrderId(orderId);
    }

    /**
     * 여러 주문 ID에 해당하는 주문 항목을 일괄 조회한다.
     *
     * @param orderIds 주문 ID 목록
     * @return 해당 주문들의 주문 항목 목록
     */
    @Override
    public List<OrderItemModel> findAllByOrderIds(List<String> orderIds) {
        return jpaRepository.findAllByOrderIdIn(orderIds);
    }
}
