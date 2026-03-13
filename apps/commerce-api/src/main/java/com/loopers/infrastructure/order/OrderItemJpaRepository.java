package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItemId;
import com.loopers.domain.order.OrderItemModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 주문 항목 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드가 자동 제공되며,
 * 복합 PK({@link OrderItemId})를 사용한다.</p>
 */
public interface OrderItemJpaRepository extends JpaRepository<OrderItemModel, OrderItemId> {

    /**
     * 주문 ID로 해당 주문의 주문 항목 목록을 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param orderId 주문 ID
     * @return 해당 주문의 주문 항목 목록
     */
    List<OrderItemModel> findAllByOrderId(Long orderId);

    /**
     * 여러 주문 ID에 해당하는 주문 항목을 일괄 조회한다.
     *
     * @param orderIds 주문 ID 목록
     * @return 해당 주문들의 주문 항목 목록
     */
    List<OrderItemModel> findAllByOrderIdIn(List<Long> orderIds);
}
