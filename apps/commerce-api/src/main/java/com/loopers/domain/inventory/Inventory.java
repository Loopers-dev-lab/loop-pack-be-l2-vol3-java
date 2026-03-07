package com.loopers.domain.inventory;

import com.loopers.domain.common.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import java.time.ZonedDateTime;

/**
 * 재고 엔티티 - 순수 POJO
 *
 * 상품별 재고 수량과 예약 수량을 관리한다.
 * 예약(reserve) → 확정(commit) / 해제(release) 패턴으로 동시성을 제어한다.
 */
public class Inventory {

    private Long id;
    private Long productId;
    private Quantity quantity;
    private Quantity reservedQty;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected Inventory() {}

    private Inventory(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = new Quantity(quantity);
        this.reservedQty = Quantity.zero();
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static Inventory reconstitute(
        Long id,
        Long productId,
        int quantity,
        int reservedQty,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Inventory inventory = new Inventory();
        inventory.id = id;
        inventory.productId = productId;
        inventory.quantity = new Quantity(quantity);
        inventory.reservedQty = new Quantity(reservedQty);
        inventory.createdAt = createdAt;
        inventory.updatedAt = updatedAt;
        inventory.deletedAt = deletedAt;
        return inventory;
    }

    /** 재고 생성 팩토리 메서드 */
    public static Inventory initialize(Long productId, int quantity) {
        Inventory inventory = new Inventory(productId, quantity);
        inventory.guard();
        ZonedDateTime now = ZonedDateTime.now();
        inventory.createdAt = now;
        inventory.updatedAt = now;
        return inventory;
    }

    /** 엔티티 유효성 검증 */
    protected void guard() {
        if (this.productId == null) {
            throw new CoreException(InventoryErrorType.INVALID_QUANTITY);
        }
    }

    /**
     * 재고 소프트 삭제 (폐기, 멱등 처리)
     * 상품 연쇄 삭제 시 중복 호출될 수 있으므로, 이미 삭제된 경우 무시한다.
     */
    public void discard() {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = ZonedDateTime.now();
    }

    /**
     * 재고 복원
     */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
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
        this.updatedAt = ZonedDateTime.now();
    }

    /** 예약 확정 (결제 완료 시 실제 수량 차감) */
    public void commit(int qty) {
        Quantity commitQty = new Quantity(qty);
        this.quantity = this.quantity.minus(commitQty);
        this.reservedQty = this.reservedQty.minus(commitQty);
        this.updatedAt = ZonedDateTime.now();
    }

    /** 예약 해제 (주문 취소 시 예약 수량 복원) */
    public void release(int qty) {
        Quantity releaseQty = new Quantity(qty);
        this.reservedQty = this.reservedQty.minus(releaseQty);
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return this.id;
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

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}
