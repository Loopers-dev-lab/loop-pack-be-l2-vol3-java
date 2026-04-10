package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "ranking_delta_pending")
public class RankingDeltaPending extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "ranking_date", nullable = false)
    private LocalDate rankingDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "delta", nullable = false)
    private Double delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RankingDeltaPendingStatus status;

    protected RankingDeltaPending() {}

    public RankingDeltaPending(String eventId, LocalDate rankingDate, Long productId, Double delta) {
        this.eventId = eventId;
        this.rankingDate = rankingDate;
        this.productId = productId;
        this.delta = delta;
        this.status = RankingDeltaPendingStatus.PENDING;
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDate getRankingDate() {
        return rankingDate;
    }

    public Long getProductId() {
        return productId;
    }

    public Double getDelta() {
        return delta;
    }

    public RankingDeltaPendingStatus getStatus() {
        return status;
    }
}
