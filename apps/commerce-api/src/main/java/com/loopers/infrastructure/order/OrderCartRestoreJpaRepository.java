package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderCartRestoreModel;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 주문-장바구니 복원 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드(save, findById, findAll, delete 등)가 자동 제공된다.
 * PK가 order_id이므로, 동일 주문에 대해 1회만 복원이 수행됨을 보장한다.</p>
 */
public interface OrderCartRestoreJpaRepository extends JpaRepository<OrderCartRestoreModel, String> {
}
