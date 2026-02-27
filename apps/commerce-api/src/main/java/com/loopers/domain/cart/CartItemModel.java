package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장바구니 항목 JPA 엔티티.
 * <p>
 * 복합 PK({@code userId} + {@code productId})를 사용하여 사용자당 상품별 1개 항목만 존재한다.
 * 수량 변경, 수량 병합(동일 상품 재등록 또는 주문 취소/만료 시 복원) 기능을 제공한다.
 * </p>
 */
@Entity
@Table(name = "cart_items")
@IdClass(CartItemId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemModel {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Id
    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private CartItemModel(String userId, String productId, int quantity) {
        validateQuantity(quantity);
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
    }

    /**
     * 장바구니 항목을 생성한다. 복합 PK(userId + productId)로 사용자당 상품별 1개 항목만 존재.
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @param quantity  수량 (1 이상)
     * @return 생성된 CartItemModel 인스턴스
     * @throws CoreException quantity <= 0인 경우 (BAD_REQUEST)
     */
    public static CartItemModel create(String userId, String productId, int quantity) {
        return new CartItemModel(userId, productId, quantity);
    }

    /**
     * 장바구니 수량을 직접 변경한다.
     *
     * @param quantity 새 수량 (1 이상)
     * @throws CoreException quantity <= 0인 경우 (BAD_REQUEST)
     */
    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    /**
     * 기존 수량에 추가 수량을 병합한다. 동일 상품 재등록 또는 DIRECT 주문 취소/만료 시 장바구니 복원에 사용.
     *
     * @param additionalQuantity 추가할 수량
     */
    public void mergeQuantity(int additionalQuantity) {
        this.quantity += additionalQuantity;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}
