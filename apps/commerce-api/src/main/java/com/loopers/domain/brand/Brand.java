package com.loopers.domain.brand;

import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;

/**
 * 브랜드 엔티티 (Aggregate Root) - 순수 POJO
 *
 * 브랜드의 생성, 수정, 상태 변경, 삭제를 담당한다.
 * 초기 상태는 ACTIVE이며, 소프트 삭제를 지원한다.
 */
public class Brand {

    private Long id;
    private String name;
    private String description;
    private BrandStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected Brand() {}

    private Brand(String name, String description) {
        this.name = name;
        this.description = description;
        this.status = BrandStatus.ACTIVE; // 생성 시 기본 ACTIVE
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static Brand reconstitute(
        Long id,
        String name,
        String description,
        BrandStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Brand brand = new Brand();
        brand.id = id;
        brand.name = name;
        brand.description = description;
        brand.status = status;
        brand.createdAt = createdAt;
        brand.updatedAt = updatedAt;
        brand.deletedAt = deletedAt;
        return brand;
    }

    /** 브랜드 생성 팩토리 메서드 */
    public static Brand create(String name, String description) {
        Brand brand = new Brand(name, description);
        brand.guard();
        ZonedDateTime now = ZonedDateTime.now();
        brand.createdAt = now;
        brand.updatedAt = now;
        return brand;
    }

    /**
     * 엔티티 유효성 검증
     * ERD 제약: name VARCHAR(150) NOT NULL
     */
    protected void guard() {
        if (this.name == null || this.name.isBlank()) {
            throw new CoreException(BrandErrorType.INVALID_BRAND_NAME);
        }
    }

    /** 브랜드 정보 부분 수정 (null이면 기존값 유지, 빈값이면 검증 에러) */
    public void changeInfo(String name, String description) {
        if (name != null) {
            if (name.isBlank()) {
                throw new CoreException(BrandErrorType.INVALID_BRAND_NAME);
            }
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        this.updatedAt = ZonedDateTime.now();
    }

    /** 브랜드 상태 변경 (ACTIVE ↔ INACTIVE) */
    public void changeStatus(BrandStatus status) {
        this.status = status;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 브랜드 소프트 삭제
     * 이미 삭제된 브랜드는 예외를 던진다.
     */
    public void delete() {
        assertNotDeleted(); // 이미 삭제된 경우 409 Conflict
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /**
     * 브랜드 복원
     */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
    }

    /** 브랜드가 활성 상태인지 확인 */
    public boolean isActive() {
        return this.status == BrandStatus.ACTIVE;
    }

    /** 비활성 브랜드에 대한 작업을 방지하는 단언 메서드 */
    public void assertActive() {
        if (this.status != BrandStatus.ACTIVE) {
            throw new CoreException(BrandErrorType.INACTIVE_BRAND);
        }
    }

    /** 삭제된 브랜드에 대한 작업을 방지하는 단언 메서드 */
    public void assertNotDeleted() {
        if (this.deletedAt != null) {
            throw new CoreException(BrandErrorType.ALREADY_DELETED);
        }
    }

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public BrandStatus getStatus() {
        return this.status;
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
