package com.loopers.domain.ranking;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * 주간 MV 읽기 전용 엔티티 (commerce-api용).
 * commerce-batch에서 적재한 mv_product_rank_weekly 테이블을 조회만 한다.
 * @Immutable: Hibernate의 dirty checking 대상에서 제외 (읽기 전용 최적화)
 */
@Entity
@Immutable
@Table(name = "mv_product_rank_weekly")
@Getter
public class MvProductRankWeeklyReadModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long salesCount;

    @Column(nullable = false)
    private double score;

    @Column(name = "`ranking`", nullable = false)
    private int ranking;

    @Column(name = "year_week", nullable = false, length = 10)
    private String yearWeek;

    protected MvProductRankWeeklyReadModel() {}
}
