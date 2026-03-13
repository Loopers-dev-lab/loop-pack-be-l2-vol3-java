package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItemId;
import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 도메인 {@link CartItemRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link CartItemJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartItemRepository {

    private final CartItemJpaRepository jpaRepository;

    /**
     * 장바구니 항목을 저장한다.
     *
     * @param item 저장할 장바구니 항목 엔티티
     * @return 저장된 장바구니 항목 엔티티
     */
    @Override
    public CartItemModel save(CartItemModel item) {
        return jpaRepository.save(item);
    }

    /**
     * 복합 PK로 장바구니 항목을 조회한다.
     *
     * @param id 복합 PK (사용자 ID + 상품 ID)
     * @return 장바구니 항목 (Optional)
     */
    @Override
    public Optional<CartItemModel> findById(CartItemId id) {
        return jpaRepository.findById(id);
    }

    /**
     * 장바구니 항목을 삭제한다.
     *
     * @param item 삭제할 장바구니 항목 엔티티
     */
    @Override
    public void delete(CartItemModel item) {
        jpaRepository.delete(item);
    }

    /**
     * 사용자 ID로 해당 사용자의 장바구니 항목 전체를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 장바구니 항목 목록
     */
    @Override
    public List<CartItemModel> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserId(userId);
    }
}
