package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 이벤트 유형별 가중치 원장.
 *
 * DB가 원장이고, Redis에 짧은 TTL로 캐시한다.
 * Consumer/Scheduler는 Redis 우선 조회, miss 시 DB fallback.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "ranking_weight",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ranking_weight_event_type",
                columnNames = "event_type"
        )
)
public class RankingWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal weight;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public RankingWeight(String eventType, BigDecimal weight) {
        this.eventType = eventType;
        this.weight = weight;
    }

    @PrePersist
    private void prePersist() {
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
