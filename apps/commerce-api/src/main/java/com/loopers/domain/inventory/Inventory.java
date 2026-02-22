package com.loopers.domain.inventory;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 재고 엔티티
 *
 * 상품별 재고 수량과 예약 수량을 관리한다.
 * 예약(reserve) → 확정(commit) / 해제(release) 패턴으로 동시성을 제어한다.
 */
@Entity
@Table(name = "inventories")
public class Inventory extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "reserved_qty", nullable = false))
    private Quantity reservedQty;

    protected Inventory() {}

    private Inventory(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = new Quantity(quantity);
        this.reservedQty = Quantity.zero();
    }

    /** 재고 생성 팩토리 메서드 */
    public static Inventory create(Long productId, int quantity) {
        return new Inventory(productId, quantity);
    }

    /** 엔티티 유효성 검증 (PrePersist, PreUpdate 시점에 호출) */
    @Override
    protected void guard() {
        if (this.productId == null) {
            throw new CoreException(InventoryErrorType.INVALID_QUANTITY);
        }
    }

    /**
     * 재고 소프트 삭제 (멱등 처리)
     * 상품 연쇄 삭제 시 중복 호출될 수 있으므로, 이미 삭제된 경우 무시한다.
     */
    @Override
    public void delete() {
        if (getDeletedAt() != null) {
            return;
        }
        super.delete();
    }

    /** 가용 재고 = 총 수량 - 예약 수량 */
    public int getAvailableQuantity() {
        return this.quantity.minus(this.reservedQty).toInt();
    }

    /** 재고 예약 (주문 시 가용 재고 차감) */
    public void reserve(int qty) {
        Quantity requestQty = new Quantity(qty);
        if (!requestQty.isPositive()) {
            throw new CoreException(InventoryErrorType.INVALID_QUANTITY);
        }
        Quantity available = this.quantity.minus(this.reservedQty);
        if (!available.isGreaterThanOrEqual(requestQty)) {
            throw new CoreException(InventoryErrorType.INSUFFICIENT_STOCK);
        }
        this.reservedQty = this.reservedQty.plus(requestQty);
    }

    /** 예약 확정 (결제 완료 시 실제 수량 차감) */
    public void commit(int qty) {
        Quantity commitQty = new Quantity(qty);
        this.quantity = this.quantity.minus(commitQty);
        this.reservedQty = this.reservedQty.minus(commitQty);
    }

    /** 예약 해제 (주문 취소 시 예약 수량 복원) */
    public void release(int qty) {
        Quantity releaseQty = new Quantity(qty);
        this.reservedQty = this.reservedQty.minus(releaseQty);
    }

    public Long getProductId() {
        return this.productId;
    }

    public int getQuantity() {
        return this.quantity.toInt();
    }

    public int getReservedQty() {
        return this.reservedQty.toInt();
    }
}
