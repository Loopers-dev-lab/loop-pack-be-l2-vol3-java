package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductRankWeeklyModelTest {

    @DisplayName("ProductRankWeekly 생성 시 모든 필드가 설정된다")
    @Test
    void shouldCreateWithAllFields() {
        Long productId = 1L;
        int rank = 1;
        double score = 42.5;
        int viewCount = 100;
        int likeCount = 50;
        int saleCount = 30;
        LocalDate startDate = LocalDate.of(2026, 4, 7);
        LocalDate endDate = LocalDate.of(2026, 4, 13);
        long version = 1L;

        var ranking = new ProductRankWeekly(productId, rank, score, viewCount, likeCount, saleCount, startDate, endDate, version);

        assertAll(
            () -> assertThat(ranking.getProductId()).isEqualTo(productId),
            () -> assertThat(ranking.getRanking()).isEqualTo(rank),
            () -> assertThat(ranking.getScore()).isEqualTo(score),
            () -> assertThat(ranking.getViewCount()).isEqualTo(viewCount),
            () -> assertThat(ranking.getLikeCount()).isEqualTo(likeCount),
            () -> assertThat(ranking.getSaleCount()).isEqualTo(saleCount),
            () -> assertThat(ranking.getStartDate()).isEqualTo(startDate),
            () -> assertThat(ranking.getEndDate()).isEqualTo(endDate),
            () -> assertThat(ranking.getVersion()).isEqualTo(version)
        );
    }
}
