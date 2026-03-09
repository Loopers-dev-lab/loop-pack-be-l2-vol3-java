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
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Id
    @Column(name = "order_item_seq")
    private int orderItemSeq;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

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

    @Column(name = "del_yn", nullable = false, length = 1)
    private String delYn = "N";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private OrderItemModel(String orderId, int orderItemSeq, String userId,
                           String productId, int quantity,
                           String snapshotProductName, BigDecimal snapshotUnitPrice,
                           String snapshotBrandId, String snapshotBrandName,
                           String snapshotImageUrl) {
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
    }

    /**
     * 주문 항목 엔티티를 생성한다. 주문 시점의 상품 정보를 스냅샷으로 보존한다.
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param orderId             주문 ID
     * @param orderItemSeq        주문 내 항목 순번
     * @param userId              주문자 ID
     * @param productId           상품 ID
     * @param quantity            주문 수량 (1 이상)
     * @param snapshotProductName 주문 시점 상품명
     * @param snapshotUnitPrice   주문 시점 단가
     * @param snapshotBrandId     주문 시점 브랜드 ID
     * @param snapshotBrandName   주문 시점 브랜드명
     * @param snapshotImageUrl    주문 시점 이미지 URL
     * @return 생성된 OrderItemModel 인스턴스
     * @throws CoreException quantity <= 0인 경우 (BAD_REQUEST)
     */
    public static OrderItemModel create(String orderId, int orderItemSeq, String userId,
                                         String productId, int quantity,
                                         String snapshotProductName, BigDecimal snapshotUnitPrice,
                                         String snapshotBrandId, String snapshotBrandName,
                                         String snapshotImageUrl) {
        return new OrderItemModel(orderId, orderItemSeq, userId, productId, quantity,
                snapshotProductName, snapshotUnitPrice, snapshotBrandId, snapshotBrandName,
                snapshotImageUrl);
    }

    /**
     * 주문 항목 소계를 계산한다 (snapshotUnitPrice x quantity).
     *
     * @return 소계 금액
     */
    public BigDecimal getSubtotal() {
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
