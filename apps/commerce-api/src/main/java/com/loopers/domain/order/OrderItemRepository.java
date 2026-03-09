package com.loopers.domain.order;

import java.util.List;

/**
 * 주문 항목 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code OrderItemRepositoryImpl}이 구현한다.
 * </p>
 */
public interface OrderItemRepository {

    /**
     * 주문 항목을 저장한다.
     *
     * @param item 저장할 주문 항목 엔티티
     * @return 저장된 주문 항목 엔티티
     */
    OrderItemModel save(OrderItemModel item);

    /**
     * 주문 항목 목록을 일괄 저장한다.
     *
     * @param items 저장할 주문 항목 엔티티 목록
     * @return 저장된 주문 항목 엔티티 목록
     */
    List<OrderItemModel> saveAll(List<OrderItemModel> items);

    /**
     * 특정 주문의 모든 주문 항목을 조회한다.
     *
     * @param orderId 주문 ID
     * @return 해당 주문의 주문 항목 목록
     */
    List<OrderItemModel> findAllByOrderId(String orderId);

    /**
     * 여러 주문 ID에 해당하는 주문 항목을 일괄 조회한다.
     *
     * @param orderIds 주문 ID 목록
     * @return 해당 주문들의 주문 항목 목록
     */
    List<OrderItemModel> findAllByOrderIds(List<String> orderIds);
}
