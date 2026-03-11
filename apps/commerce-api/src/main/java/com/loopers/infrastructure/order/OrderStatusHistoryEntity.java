package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.OrderStatusHistory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * OrderStatusHistory DB 엔티티
 */
@Entity
@Table(name = "order_status_history")
@NoArgsConstructor
public class OrderStatusHistoryEntity extends BaseEntity {

    @Comment("주문 id (ref)")
    @Column(name = "ref_order_id", insertable = false, updatable = false)
    private Long refOrderId;

    @Comment("주문 상태")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private OrderStatus status;

    @Comment("상태 변경 일시")
    @Column(name = "changed_at", nullable = false, updatable = false)
    private ZonedDateTime changedAt;

    private OrderStatusHistoryEntity(Long refOrderId, OrderStatus status, ZonedDateTime changedAt) {
        this.refOrderId = refOrderId;
        this.status = status;
        this.changedAt = changedAt;
    }

    public static OrderStatusHistoryEntity create(OrderStatusHistory history) {
        return new OrderStatusHistoryEntity(
            history.refOrderId(),
            history.status(),
            history.changedAt()
        );
    }

    public OrderStatusHistory toDomain() {
        return OrderStatusHistory.create(
            this.getId(),
            this.refOrderId,
            this.status,
            this.changedAt
        );
    }

    public Long getRefOrderId() {
        return refOrderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public ZonedDateTime getChangedAt() {
        return changedAt;
    }
}
