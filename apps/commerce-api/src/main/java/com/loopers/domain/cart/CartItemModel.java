package com.loopers.domain.cart;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

import static lombok.AccessLevel.PROTECTED;

/**
 * 장바구니 항목 도메인 엔티티.
 * 동일 상품·동일 옵션은 수량 합산으로 처리한다. Hard delete 사용.
 */
@Entity
@Table(name = "cart")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class CartItemModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private CartItemModel(Long userId, Long productId, Long optionId, int quantity) {
        this.userId = userId;
        this.productId = productId;
        this.optionId = optionId;
        this.quantity = quantity;
    }

    /**
     * 유효한 값으로 장바구니 항목을 생성한다.
     *
     * @param userId    사용자 ID (not null)
     * @param productId 상품 ID (not null)
     * @param optionId  옵션 ID (null 가능, 현재 단계에서는 검증 없이 보존)
     * @param quantity  수량 (1 이상)
     * @return 생성된 CartItemModel
     */
    public static CartItemModel create(Long userId, Long productId, Long optionId, Quantity quantity) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 null일 수 없습니다.");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 null일 수 없습니다.");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("수량은 null일 수 없습니다.");
        }
        return new CartItemModel(userId, productId, optionId, quantity.value());
    }

    /**
     * 동일 상품·동일 옵션인지 판단한다.
     */
    public boolean isSameProduct(Long productId, Long optionId) {
        if (!Objects.equals(this.productId, productId)) {
            return false;
        }
        return Objects.equals(this.optionId, optionId);
    }

    /**
     * 수량을 갱신한다. 동일 품목 합산 시 사용.
     *
     * @param quantity 1 이상
     */
    public void updateQuantity(Quantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("수량은 null일 수 없습니다.");
        }
        this.quantity = quantity.value();
    }

    /**
     * 수량과 옵션을 갱신한다. 장바구니 수정 시 사용.
     *
     * @param quantity 1 이상
     * @param optionId 옵션 ID (null 가능)
     */
    public void updateQuantityAndOption(Quantity quantity, Long optionId) {
        if (quantity == null) {
            throw new IllegalArgumentException("수량은 null일 수 없습니다.");
        }
        this.quantity = quantity.value();
        this.optionId = optionId;
    }
}
