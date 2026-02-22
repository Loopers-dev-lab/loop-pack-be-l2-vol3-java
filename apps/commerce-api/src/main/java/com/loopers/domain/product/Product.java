package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ProductErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 상품 엔티티 (Aggregate Root)
 *
 * 상품의 생성, 수정, 상태 변경, 삭제를 담당한다.
 * 초기 상태는 ACTIVE이며, 소프트 삭제를 지원한다.
 */
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "base_price", nullable = false))
    private Money basePrice;

    /** 상품 노출 상태 (ACTIVE, SOLDOUT, HIDDEN, DISCONTINUED) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    protected Product() {}

    private Product(Long brandId, String name, String description, int basePrice) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.basePrice = new Money(basePrice);
        this.status = ProductStatus.ACTIVE;
        this.likeCount = 0;
    }

    /** 상품 생성 팩토리 메서드 */
    public static Product create(Long brandId, String name, String description, int basePrice) {
        return new Product(brandId, name, description, basePrice);
    }

    /**
     * 엔티티 유효성 검증 (PrePersist, PreUpdate 시점에 호출)
     * ERD 제약: name VARCHAR(200) NOT NULL
     */
    @Override
    protected void guard() {
        if (this.name == null || this.name.isBlank()) {
            throw new CoreException(ProductErrorType.INVALID_PRODUCT_NAME);
        }
    }

    /** 상품 정보 수정 (이름, 설명, 기본가격) */
    public void update(String name, String description, int basePrice) {
        this.name = name;
        this.description = description;
        this.basePrice = new Money(basePrice);
    }

    /** 상품 상태 변경 (ACTIVE, SOLDOUT, HIDDEN, DISCONTINUED) */
    public void changeStatus(ProductStatus status) {
        this.status = status;
    }

    /**
     * 상품 소프트 삭제
     * BaseEntity의 멱등 delete()를 override하여, 이미 삭제된 상품은 예외를 던진다.
     */
    @Override
    public void delete() {
        assertNotDeleted();
        super.delete();
    }

    /** 삭제된 상품에 대한 작업을 방지하는 단언 메서드 */
    public void assertNotDeleted() {
        if (getDeletedAt() != null) {
            throw new CoreException(ProductErrorType.ALREADY_DELETED);
        }
    }

    /** 고객에게 노출 가능한 상태인지 확인 (ACTIVE, SOLDOUT) */
    public boolean isDisplayable() {
        return this.status == ProductStatus.ACTIVE || this.status == ProductStatus.SOLDOUT;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public Long getBrandId() {
        return this.brandId;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public int getBasePrice() {
        return this.basePrice.toInt();
    }

    public ProductStatus getStatus() {
        return this.status;
    }

    public int getLikeCount() {
        return this.likeCount;
    }
}
