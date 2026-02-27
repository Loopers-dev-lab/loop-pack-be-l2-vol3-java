package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * del_yn/deletedAt 이중 관리 + 공통 타임스탬프 기반 엔티티.
 * 신규 도메인(User, Brand, Product, Order 등)에서 사용한다.
 * PK(@Id)는 서브클래스에서 @UuidGenerator로 직접 정의한다.
 * 기존 BaseEntity(Long PK)와 공존한다.
 */
@MappedSuperclass
@Getter
public abstract class BaseStringIdEntity {

    @Column(name = "del_yn", nullable = false, length = 1)
    private String delYn = "N";

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /**
     * 서브클래스에서 오버라이드하여 엔티티 유효성 검증에 사용한다.
     */
    protected void guard() {}

    @PrePersist
    private void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        guard();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
        guard();
    }

    /**
     * 소프트 삭제 (멱등).
     * del_yn='Y' + deletedAt=now()
     */
    public void softDelete() {
        if ("N".equals(this.delYn)) {
            this.delYn = "Y";
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /**
     * 복원 (멱등).
     * del_yn='N' + deletedAt=null
     */
    public void restore() {
        if ("Y".equals(this.delYn)) {
            this.delYn = "N";
            this.deletedAt = null;
        }
    }

    /**
     * 엔티티의 소프트 삭제 여부를 반환한다.
     *
     * @return 삭제된 경우 {@code true}, 아닌 경우 {@code false}
     */
    public boolean isDeleted() {
        return "Y".equals(this.delYn);
    }
}
