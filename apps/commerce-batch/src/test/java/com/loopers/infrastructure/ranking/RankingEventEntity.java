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
 * 테스트 전용 RankingEventEntity 복제.
 *
 * commerce-batch 메인 소스는 ranking_event 테이블을 JdbcCursorItemReader로
 * raw SQL 스트리밍만 하므로 JPA 엔티티가 필요 없다. 하지만 통합 테스트에서는
 * JPA DDL-auto가 테이블을 생성해야 시드를 넣을 수 있으므로 test 클래스패스에만
 * 동일 매핑을 복제한다.
 *
 * commerce-streamer의 RankingEventEntity와 스키마 동기화 필요 — 변경 시 양쪽 갱신.
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

    public Long getId() { return id; }
    public String getOutboxId() { return outboxId; }
    public Long getProductId() { return productId; }
    public String getEventType() { return eventType; }
    public ZonedDateTime getEventTime() { return eventTime; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
}
