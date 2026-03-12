package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * 상품 도메인 엔티티.
 * 순수 Java 객체로 JPA/Spring 의존성 없음.
 */
public class Product {

    private Long id;
    private Long brandId;
    private String name;
    private String description;
    private Money price;
    private Stock stock;
    private String imageUrl;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    private Product() {}

    /**
     * 새 상품 생성.
     */
    public static Product create(Long brandId, String name, String description,
            Money price, Stock stock, String imageUrl) {
        Product product = new Product();
        product.brandId = brandId;
        product.name = name;
        product.description = description;
        product.price = price;
        product.stock = stock;
        product.imageUrl = imageUrl;
        ZonedDateTime now = ZonedDateTime.now();
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static Product reconstitute(Long id, Long brandId, String name, String description,
            Money price, Stock stock, String imageUrl,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        Product product = new Product();
        product.id = id;
        product.brandId = brandId;
        product.name = name;
        product.description = description;
        product.price = price;
        product.stock = stock;
        product.imageUrl = imageUrl;
        product.createdAt = createdAt;
        product.updatedAt = updatedAt;
        product.deletedAt = deletedAt;
        return product;
    }

    /**
     * 재고 차감.
     *
     * @param quantity 차감할 수량
     * @throws CoreException 삭제된 상품이거나 재고가 부족한 경우
     */
    public void decreaseStock(int quantity) {
        guardDeleted();
        this.stock = this.stock.decrease(quantity);
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 상품 정보 수정.
     *
     * @throws CoreException 삭제된 상품인 경우
     */
    public void update(String name, String description, Money price, Stock stock, String imageUrl) {
        guardDeleted();
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 상품 삭제 (Soft Delete).
     * 멱등하게 동작한다.
     */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void guardDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.PRODUCT_DELETED);
        }
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public Stock getStock() {
        return stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
