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

    public static Inventory create(Long productId, int quantity) {
        return new Inventory(productId, quantity);
    }

    public int getAvailableQuantity() {
        return this.quantity.minus(this.reservedQty).toInt();
    }

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

    public void commit(int qty) {
        Quantity commitQty = new Quantity(qty);
        this.quantity = this.quantity.minus(commitQty);
        this.reservedQty = this.reservedQty.minus(commitQty);
    }

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
