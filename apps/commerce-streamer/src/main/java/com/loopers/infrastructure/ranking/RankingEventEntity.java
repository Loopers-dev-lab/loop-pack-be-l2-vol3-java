package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

/**
 * 랭킹 원천 이벤트 테이블 — 사실(fact)만 저장한다
 *
 * "무슨 일이 일어났는가"만 기록하고, "그게 몇 점인가"는 저장하지 않는다.
 * 가중치는 조회 시점에 적용하므로, 가중치 변경 시 언제든 재계산 가능.
 *
 * 멱등성: (outbox_id, product_id) 복합 UNIQUE로 중복 INSERT 방어.
 * 이 테이블 자체가 멱등성 경계 역할을 한다.
 *
 * OrderItemSoldEvent 확장: 주문 1건에 상품 N개 → N행 적재.
 * (outbox_id='order-456', product_id=101), (outbox_id='order-456', product_id=202)
 */
@Entity
@Table(name = "ranking_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ranking_event_outbox_product",
                        columnNames = {"outbox_id", "product_id"})
        },
        indexes = {
                @Index(name = "idx_ranking_event_product_type_time",
                        columnList = "product_id, event_type, event_time")
        })
public class RankingEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id", nullable = false, length = 100)
    private String outboxId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "event_time", nullable = false)
    private ZonedDateTime eventTime;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected RankingEventEntity() {}

    public static RankingEventEntity of(String outboxId, Long productId,
                                         String eventType, ZonedDateTime eventTime) {
        RankingEventEntity entity = new RankingEventEntity();
        entity.outboxId = outboxId;
        entity.productId = productId;
        entity.eventType = eventType;
        entity.eventTime = eventTime;
        entity.createdAt = ZonedDateTime.now();
        return entity;
    }

    public Long getId() { return id; }
    public String getOutboxId() { return outboxId; }
    public Long getProductId() { return productId; }
    public String getEventType() { return eventType; }
    public ZonedDateTime getEventTime() { return eventTime; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
}
