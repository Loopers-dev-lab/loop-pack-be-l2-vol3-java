package com.loopers.infrastructure.outbox.entity;

import com.loopers.domain.outbox.model.OutboxEvent;
import com.loopers.domain.outbox.model.OutboxEventType;
import com.loopers.domain.outbox.model.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private OutboxEventType eventType;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static OutboxEventEntity toEntity(OutboxEvent model) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = model.getId();
        entity.eventType = model.getEventType();
        entity.aggregateId = model.getAggregateId();
        entity.payload = model.getPayload();
        entity.status = model.getStatus();
        entity.createdAt = model.getCreatedAt();
        return entity;
    }

    public OutboxEvent toModel() {
        return OutboxEvent.reconstruct(id, eventType, aggregateId, payload, status, createdAt);
    }

    public void updateStatus(OutboxStatus status) {
        this.status = status;
    }
}
