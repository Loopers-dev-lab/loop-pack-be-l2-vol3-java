package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * 브랜드 도메인 엔티티.
 * 순수 Java 객체로 JPA/Spring 의존성 없음.
 */
public class Brand {

    private Long id;
    private String name;
    private String description;
    private String logoUrl;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    private Brand() {}

    /**
     * 새 브랜드 생성.
     */
    public static Brand create(String name, String description, String logoUrl) {
        Brand brand = new Brand();
        brand.name = name;
        brand.description = description;
        brand.logoUrl = logoUrl;
        ZonedDateTime now = ZonedDateTime.now();
        brand.createdAt = now;
        brand.updatedAt = now;
        return brand;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static Brand reconstitute(Long id, String name, String description,
            String logoUrl, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        Brand brand = new Brand();
        brand.id = id;
        brand.name = name;
        brand.description = description;
        brand.logoUrl = logoUrl;
        brand.createdAt = createdAt;
        brand.updatedAt = updatedAt;
        brand.deletedAt = deletedAt;
        return brand;
    }

    /**
     * 브랜드 정보 수정.
     *
     * @throws CoreException 삭제된 브랜드인 경우
     */
    public void update(String name, String description, String logoUrl) {
        guardDeleted();
        this.name = name;
        this.description = description;
        this.logoUrl = logoUrl;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 브랜드 삭제 (Soft Delete).
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
            throw new CoreException(ErrorType.BRAND_DELETED);
        }
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLogoUrl() {
        return logoUrl;
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