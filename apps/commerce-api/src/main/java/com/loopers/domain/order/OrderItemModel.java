package com.loopers.domain.order;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 항목 JPA 엔티티.
 * <p>
 * 복합 PK({@code orderId} + {@code orderItemSeq})를 사용한다.
 * 주문 시점의 상품 정보를 스냅샷(snapshot)으로 보존하여,
 * 이후 상품 정보가 변경되더라도 주문 당시 정보를 유지한다.
 * </p>
 */
@Entity
@Table(name = "order_items")
@IdClass(OrderItemId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemModel {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Id
    @Column(name = "order_item_seq")
    private int orderItemSeq;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "snapshot_product_name")
    private String snapshotProductName;

    @Column(name = "snapshot_unit_price", precision = 12, scale = 2)
    private BigDecimal snapshotUnitPrice;

    @Column(name = "snapshot_brand_id", length = 36)
    private String snapshotBrandId;

    @Column(name = "snapshot_brand_name")
    private String snapshotBrandName;

    @Column(name = "snapshot_image_url")
    private String snapshotImageUrl;

    @Column(name = "original_amount", precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "del_yn", nullable = false, length = 1)
    private String delYn = "N";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private OrderItemModel(Long orderId, int orderItemSeq, Long userId,
                           Long productId, int quantity,
                           String snapshotProductName, BigDecimal snapshotUnitPrice,
                           String snapshotBrandId, String snapshotBrandName,
                           String snapshotImageUrl,
                           BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal finalAmount) {
        validateQuantity(quantity);
        this.orderId = orderId;
        this.orderItemSeq = orderItemSeq;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.snapshotProductName = snapshotProductName;
        this.snapshotUnitPrice = snapshotUnitPrice;
        this.snapshotBrandId = snapshotBrandId;
        this.snapshotBrandName = snapshotBrandName;
        this.snapshotImageUrl = snapshotImageUrl;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
    }

    /**
     * 주문 항목 엔티티를 생성한다 (하위 호환 — 할인 없음).
     */
    public static OrderItemModel create(Long orderId, int orderItemSeq, Long userId,
                                         Long productId, int quantity,
                                         String snapshotProductName, BigDecimal snapshotUnitPrice,
                                         String snapshotBrandId, String snapshotBrandName,
                                         String snapshotImageUrl) {
        BigDecimal lineTotal = snapshotUnitPrice != null
                ? snapshotUnitPrice.multiply(BigDecimal.valueOf(quantity))
                : null;
        return new OrderItemModel(orderId, orderItemSeq, userId, productId, quantity,
                snapshotProductName, snapshotUnitPrice, snapshotBrandId, snapshotBrandName,
                snapshotImageUrl, lineTotal, BigDecimal.ZERO, lineTotal);
    }

    /**
     * 주문 항목 엔티티를 생성한다 (할인 금액 포함).
     */
    public static OrderItemModel create(Long orderId, int orderItemSeq, Long userId,
                                         Long productId, int quantity,
                                         String snapshotProductName, BigDecimal snapshotUnitPrice,
                                         String snapshotBrandId, String snapshotBrandName,
                                         String snapshotImageUrl,
                                         BigDecimal originalAmount, BigDecimal discountAmount,
                                         BigDecimal finalAmount) {
        return new OrderItemModel(orderId, orderItemSeq, userId, productId, quantity,
                snapshotProductName, snapshotUnitPrice, snapshotBrandId, snapshotBrandName,
                snapshotImageUrl, originalAmount, discountAmount, finalAmount);
    }

    /**
     * 주문 항목 소계를 계산한다.
     * finalAmount가 있으면 finalAmount를, 없으면 snapshotUnitPrice x quantity를 반환한다.
     */
    public BigDecimal getSubtotal() {
        if (finalAmount != null) {
            return finalAmount;
        }
        return snapshotUnitPrice.multiply(BigDecimal.valueOf(quantity));
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
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
