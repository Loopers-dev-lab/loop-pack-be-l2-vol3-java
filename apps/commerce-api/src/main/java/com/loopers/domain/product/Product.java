package com.loopers.domain.product;

import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ProductErrorType;
import java.time.ZonedDateTime;

/**
 * 상품 엔티티 (Aggregate Root) - 순수 POJO
 *
 * 상품의 생성, 수정, 상태 변경, 삭제를 담당한다.
 * 초기 상태는 ACTIVE이며, 소프트 삭제를 지원한다.
 */
public class Product {

    private Long id;
    private Long brandId;
    private String name;
    private String description;
    private Money basePrice;
    private ProductStatus status;
    private int likeCount;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected Product() {}

    private Product(Long brandId, String name, String description, int basePrice) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.basePrice = new Money(basePrice);
        this.status = ProductStatus.ACTIVE;
        this.likeCount = 0;
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static Product reconstitute(
        Long id,
        Long brandId,
        String name,
        String description,
        Money basePrice,
        ProductStatus status,
        int likeCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Product product = new Product();
        product.id = id;
        product.brandId = brandId;
        product.name = name;
        product.description = description;
        product.basePrice = basePrice;
        product.status = status;
        product.likeCount = likeCount;
        product.createdAt = createdAt;
        product.updatedAt = updatedAt;
        product.deletedAt = deletedAt;
        return product;
    }

    /** 상품 생성 팩토리 메서드 */
    public static Product create(Long brandId, String name, String description, int basePrice) {
        Product product = new Product(brandId, name, description, basePrice);
        product.guard();
        ZonedDateTime now = ZonedDateTime.now();
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    /**
     * 엔티티 유효성 검증
     * ERD 제약: name VARCHAR(200) NOT NULL
     */
    protected void guard() {
        if (this.name == null || this.name.isBlank()) {
            throw new CoreException(ProductErrorType.INVALID_PRODUCT_NAME);
        }
    }

    /** 상품 정보 부분 수정 (null이면 기존값 유지, 빈값이면 검증 에러) */
    public void update(String name, String description, Integer basePrice) {
        if (name != null) {
            if (name.isBlank()) {
                throw new CoreException(ProductErrorType.INVALID_PRODUCT_NAME);
            }
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (basePrice != null) {
            this.basePrice = new Money(basePrice);
        }
        this.updatedAt = ZonedDateTime.now();
    }

    /** 상품 상태 변경 (ACTIVE, SOLDOUT, HIDDEN, DISCONTINUED) */
    public void changeStatus(ProductStatus status) {
        this.status = status;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 상품 소프트 삭제
     * 이미 삭제된 상품은 예외를 던진다.
     */
    public void delete() {
        assertNotDeleted();
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
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

    /** 구매 가능한 상태인지 단언 (ACTIVE만 구매 가능) */
    public void assertPurchasable() {
        if (this.status != ProductStatus.ACTIVE) {
            throw new CoreException(ProductErrorType.NOT_PURCHASABLE);
        }
    }

    public void incrementLikeCount() {
        this.likeCount++;
        this.updatedAt = ZonedDateTime.now();
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
            this.updatedAt = ZonedDateTime.now();
        }
    }

    public Long getId() {
        return this.id;
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
