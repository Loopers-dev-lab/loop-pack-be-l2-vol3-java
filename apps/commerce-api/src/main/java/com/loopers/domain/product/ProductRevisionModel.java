package com.loopers.domain.product;

import com.loopers.support.enums.ProductRevisionAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 변경 이력 JPA 엔티티.
 * <p>
 * 복합 PK({@code productId} + {@code revisionSeq})를 사용한다.
 * 상품 생성, 수정, 삭제, 복구, 판매 상태 변경 등의 이벤트를 기록하며,
 * 변경 전/후 상품 상태를 JSON 스냅샷({@code beforeSnapshot}, {@code afterSnapshot})으로 보존한다.
 * </p>
 */
@Entity
@Table(name = "product_revisions")
@IdClass(ProductRevisionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRevisionModel {

    @Id
    @Column(name = "product_id", length = 36)
    private String productId;

    @Id
    @Column(name = "revision_seq")
    private Long revisionSeq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductRevisionAction action;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "before_snapshot", columnDefinition = "json")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", columnDefinition = "json")
    private String afterSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ProductRevisionModel(String productId, Long revisionSeq, ProductRevisionAction action,
                                  String changedBy, String changeReason,
                                  String beforeSnapshot, String afterSnapshot) {
        this.productId = productId;
        this.revisionSeq = revisionSeq;
        this.action = action;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
    }

    /**
     * 상품 변경 이력 레코드를 생성한다. 복합 PK(productId + revisionSeq).
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param productId      대상 상품 ID
     * @param revisionSeq    변경 순번
     * @param action         변경 유형 (CREATE/UPDATE/HIDE/SALE_STATUS_CHANGE/DELETE/RESTORE)
     * @param changedBy      변경 수행자
     * @param changeReason   변경 사유 (선택)
     * @param beforeSnapshot 변경 전 상품 상태 JSON (CREATE 시 null)
     * @param afterSnapshot  변경 후 상품 상태 JSON (DELETE 시 null)
     * @return 생성된 ProductRevisionModel 인스턴스
     */
    public static ProductRevisionModel create(String productId, Long revisionSeq,
                                               ProductRevisionAction action,
                                               String changedBy, String changeReason,
                                               String beforeSnapshot, String afterSnapshot) {
        return new ProductRevisionModel(productId, revisionSeq, action,
                changedBy, changeReason, beforeSnapshot, afterSnapshot);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
