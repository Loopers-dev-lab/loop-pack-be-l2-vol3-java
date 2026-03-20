package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderCartRestoreModel;
import com.loopers.domain.order.OrderCartRestoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인 {@link OrderCartRestoreRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link OrderCartRestoreJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class OrderCartRestoreRepositoryImpl implements OrderCartRestoreRepository {

    private final OrderCartRestoreJpaRepository jpaRepository;

    /**
     * 주문-장바구니 복원 이력을 저장한다.
     *
     * <p>PK가 order_id이므로, 동일 주문에 대한 중복 복원 시도 시 예외가 발생하여
     * 멱등성이 보장된다.</p>
     *
     * @param restore 저장할 주문-장바구니 복원 엔티티
     * @return 저장된 주문-장바구니 복원 엔티티
     */
    @Override
    public OrderCartRestoreModel save(OrderCartRestoreModel restore) {
        return jpaRepository.save(restore);
    }

    /**
     * 주문 ID에 해당하는 장바구니 복원 기록이 존재하는지 확인한다.
     *
     * @param orderId 주문 ID
     * @return 존재 여부
     */
    @Override
    public boolean existsById(Long orderId) {
        return jpaRepository.existsById(orderId);
    }
}
