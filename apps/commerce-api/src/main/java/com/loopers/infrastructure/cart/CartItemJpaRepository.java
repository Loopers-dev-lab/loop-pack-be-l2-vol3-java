package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItemId;
import com.loopers.domain.cart.CartItemModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 장바구니 항목 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드가 자동 제공되며,
 * 복합 PK({@link CartItemId})를 사용한다.</p>
 */
public interface CartItemJpaRepository extends JpaRepository<CartItemModel, CartItemId> {

    /**
     * 사용자 ID로 해당 사용자의 장바구니 항목 전체를 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 장바구니 항목 목록
     */
    List<CartItemModel> findAllByUserId(String userId);
}
