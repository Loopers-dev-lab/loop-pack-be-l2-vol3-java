package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 브랜드 엔티티 (Aggregate Root)
 *
 * 브랜드의 생성, 수정, 상태 변경, 삭제를 담당한다.
 * 초기 상태는 ACTIVE이며, 소프트 삭제를 지원한다.
 */
@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    /** 브랜드 활성 상태 (ACTIVE, INACTIVE) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BrandStatus status;

    protected Brand() {}

    private Brand(String name, String description) {
        this.name = name;
        this.description = description;
        this.status = BrandStatus.ACTIVE; // 생성 시 기본 ACTIVE
    }

    /** 브랜드 생성 팩토리 메서드 */
    public static Brand create(String name, String description) {
        return new Brand(name, description);
    }

    /**
     * 엔티티 유효성 검증 (PrePersist, PreUpdate 시점에 호출)
     * ERD 제약: name VARCHAR(150) NOT NULL
     */
    @Override
    protected void guard() {
        if (this.name == null || this.name.isBlank()) {
            throw new CoreException(BrandErrorType.INVALID_BRAND_NAME);
        }
    }

    /** 브랜드 정보 수정 */
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** 브랜드 상태 변경 (ACTIVE ↔ INACTIVE) */
    public void changeStatus(BrandStatus status) {
        this.status = status;
    }

    /**
     * 브랜드 소프트 삭제
     * BaseEntity의 멱등 delete()를 override하여, 이미 삭제된 브랜드는 예외를 던진다.
     */
    @Override
    public void delete() {
        assertNotDeleted(); // 이미 삭제된 경우 409 Conflict
        super.delete();
    }

    /** 브랜드가 활성 상태인지 확인 */
    public boolean isActive() {
        return this.status == BrandStatus.ACTIVE;
    }

    /** 삭제된 브랜드에 대한 작업을 방지하는 단언 메서드 */
    public void assertNotDeleted() {
        if (getDeletedAt() != null) {
            throw new CoreException(BrandErrorType.ALREADY_DELETED);
        }
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
}
