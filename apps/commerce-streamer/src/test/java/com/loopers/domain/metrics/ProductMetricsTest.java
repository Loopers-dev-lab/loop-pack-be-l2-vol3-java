package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductMetricsTest {

    @DisplayName("ProductMetrics 생성 시 모든 카운트가 0이다")
    @Test
    void createsWithZeroCounts() {
        // given & when
        ProductMetrics metrics = new ProductMetrics(1L);

        // then
        assertAll(
            () -> assertThat(metrics.getProductId()).isEqualTo(1L),
            () -> assertThat(metrics.getLikeCount()).isZero(),
            () -> assertThat(metrics.getViewCount()).isZero(),
            () -> assertThat(metrics.getSaleCount()).isZero()
        );
    }

    @DisplayName("좋아요 증가/감소가 정상 동작한다")
    @Test
    void likeCountIncrementAndDecrement() {
        // given
        ProductMetrics metrics = new ProductMetrics(1L);

        // when
        metrics.incrementLikeCount();
        metrics.incrementLikeCount();
        metrics.decrementLikeCount();

        // then
        assertThat(metrics.getLikeCount()).isEqualTo(1);
    }

    @DisplayName("좋아요 카운트가 0 이하로 내려가지 않는다")
    @Test
    void likeCountFloorAtZero() {
        // given
        ProductMetrics metrics = new ProductMetrics(1L);

        // when
        metrics.decrementLikeCount();

        // then
        assertThat(metrics.getLikeCount()).isZero();
    }

    @DisplayName("조회수 증가가 정상 동작한다")
    @Test
    void viewCountIncrement() {
        // given
        ProductMetrics metrics = new ProductMetrics(1L);

        // when
        metrics.incrementViewCount();
        metrics.incrementViewCount();

        // then
        assertThat(metrics.getViewCount()).isEqualTo(2);
    }

    @DisplayName("판매 수량 증가가 정상 동작한다")
    @Test
    void saleCountIncrement() {
        // given
        ProductMetrics metrics = new ProductMetrics(1L);

        // when
        metrics.incrementSaleCount(3);

        // then
        assertThat(metrics.getSaleCount()).isEqualTo(3);
    }
}
