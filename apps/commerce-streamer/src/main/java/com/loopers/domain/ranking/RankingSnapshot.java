package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ranking_snapshot", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "score_date"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDate scoreDate;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
