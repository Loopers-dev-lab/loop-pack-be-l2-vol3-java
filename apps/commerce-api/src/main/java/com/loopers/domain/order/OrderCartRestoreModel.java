package com.loopers.domain.order;

import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 주문 장바구니 복원 기록 JPA 엔티티.
 * <p>
 * DIRECT 주문 취소/만료 시 장바구니 복원이 수행되었음을 기록한다.
 * PK가 {@code orderId}이므로 주문당 1회만 복원 가능하며(멱등 보장),
 * 2번째 INSERT 시 PK 충돌이 발생하여 중복 복원을 방지한다.
 * </p>
 */
@Entity
@Table(name = "order_cart_restore")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCartRestoreModel {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RestoreReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 30)
    private RestoreTriggerSource triggerSource;

    @Column(name = "restored_at", nullable = false)
    private LocalDateTime restoredAt;

    private OrderCartRestoreModel(Long orderId, Long userId,
                                   RestoreReason reason, RestoreTriggerSource triggerSource) {
        this.orderId = orderId;
        this.userId = userId;
        this.reason = reason;
        this.triggerSource = triggerSource;
    }

    /**
     * 장바구니 복원 기록을 생성한다. PK가 orderId이므로 주문당 1회만 복원 가능 (멱등 보장).
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param orderId       주문 ID (PK -- 2번째 INSERT 시 PK 충돌 발생)
     * @param userId        사용자 ID
     * @param reason        복원 사유 (USER_CANCELLED / EXPIRED 등)
     * @param triggerSource 복원 트리거 출처 (CANCEL_API / EXPIRE_JOB 등)
     * @return 생성된 OrderCartRestoreModel 인스턴스
     */
    public static OrderCartRestoreModel create(Long orderId, Long userId,
                                                RestoreReason reason, RestoreTriggerSource triggerSource) {
        return new OrderCartRestoreModel(orderId, userId, reason, triggerSource);
    }

    @PrePersist
    private void prePersist() {
        this.restoredAt = LocalDateTime.now();
    }
}
