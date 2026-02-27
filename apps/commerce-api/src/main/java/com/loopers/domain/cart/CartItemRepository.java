package com.loopers.domain.cart;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 항목 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code CartItemRepositoryImpl}이 구현한다.
 * </p>
 */
public interface CartItemRepository {

    /**
     * 장바구니 항목을 저장한다.
     *
     * @param item 저장할 장바구니 항목 엔티티
     * @return 저장된 장바구니 항목 엔티티
     */
    CartItemModel save(CartItemModel item);

    /**
     * 복합 PK(userId + productId)로 장바구니 항목을 조회한다.
     *
     * @param id 장바구니 항목 복합 기본키
     * @return 장바구니 항목 (존재하지 않으면 빈 Optional)
     */
    Optional<CartItemModel> findById(CartItemId id);

    /**
     * 장바구니 항목을 삭제한다.
     *
     * @param item 삭제할 장바구니 항목 엔티티
     */
    void delete(CartItemModel item);

    /**
     * 특정 사용자의 모든 장바구니 항목을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 장바구니 항목 목록
     */
    List<CartItemModel> findAllByUserId(String userId);
}
