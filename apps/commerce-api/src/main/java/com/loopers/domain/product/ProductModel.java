package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/**
 * 상품 도메인 엔티티.
 * Soft delete는 BaseEntity의 deletedAt으로 표현한다.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ProductModel extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    private ProductModel(Long brandId, String name, BigDecimal price, int stockQuantity) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    /**
     * 유효한 값으로 상품을 생성한다.
     *
     * @param brandId       브랜드 ID (not null)
     * @param name          상품명 (null·빈 문자열·공백만 불가)
     * @param price         가격 (0 이상)
     * @param stockQuantity 재고 수량 (0 이상)
     * @return 생성된 ProductModel
     */
    public static ProductModel create(Long brandId, String name, Money price, StockQuantity stockQuantity) {
        if (brandId == null) {
            throw new IllegalArgumentException("브랜드 ID는 null일 수 없습니다.");
        }
        if (name == null) {
            throw new IllegalArgumentException("상품명은 null일 수 없습니다.");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다.");
        }
        if (price == null) {
            throw new IllegalArgumentException("가격은 null일 수 없습니다.");
        }
        if (stockQuantity == null) {
            throw new IllegalArgumentException("재고 수량은 null일 수 없습니다.");
        }
        return new ProductModel(brandId, name.trim(), price.value(), stockQuantity.value());
    }

    /**
     * 재고가 주어진 수량 이상인지 확인한다.
     */
    public boolean hasStock(Quantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("수량은 null일 수 없습니다.");
        }
        return this.stockQuantity >= quantity.value();
    }

    /**
     * 상품명을 수정한다. (브랜드 변경 불가)
     */
    public void updateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("상품명은 null일 수 없습니다.");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다.");
        }
        this.name = name.trim();
    }

    /**
     * 가격을 수정한다.
     */
    public void updatePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("가격은 null일 수 없습니다.");
        }
        this.price = price.value();
    }

    /**
     * 재고 수량을 수정한다.
     */
    public void updateStockQuantity(StockQuantity stockQuantity) {
        if (stockQuantity == null) {
            throw new IllegalArgumentException("재고 수량은 null일 수 없습니다.");
        }
        this.stockQuantity = stockQuantity.value();
    }

    /**
     * 재고를 복구(증가)한다. 취소 등으로 재고를 되돌릴 때 사용.
     */
    public void increaseStock(Quantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("복구 수량은 null일 수 없습니다.");
        }
        this.stockQuantity += quantity.value();
    }

    /**
     * 삭제 여부를 반환한다.
     */
    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    /**
     * 주문용 스냅샷(상품 ID, 이름, 가격)을 반환한다.
     */
    public ProductSnapshot snapshotForOrder() {
        return new ProductSnapshot(getId(), name, Money.of(price));
    }
}
