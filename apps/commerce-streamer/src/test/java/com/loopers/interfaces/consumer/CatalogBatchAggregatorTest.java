package com.loopers.interfaces.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CatalogBatchAggregator 단위 테스트")
class CatalogBatchAggregatorTest {

    @Test
    @DisplayName("같은 productId 좋아요 이벤트 -> 마지막 스냅샷만 남음")
    void addLikeEvent_SameProduct_ShouldKeepLast() {
        CatalogBatchAggregator aggregator = new CatalogBatchAggregator();

        aggregator.addLikeEvent(1L, 10L, LocalDateTime.now(), 100L);
        aggregator.addLikeEvent(1L, 12L, LocalDateTime.now(), 101L);

        assertThat(aggregator.getLikeDeltas()).hasSize(1);
        assertThat(aggregator.getLikeDeltas().get(1L).likeCount()).isEqualTo(12L);
        assertThat(aggregator.getProcessedEventIds()).containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    @DisplayName("조회수 이벤트 -> 같은 productId는 합산")
    void addViewEvent_SameProduct_ShouldSum() {
        CatalogBatchAggregator aggregator = new CatalogBatchAggregator();

        aggregator.addViewEvent(1L, 200L);
        aggregator.addViewEvent(1L, 201L);
        aggregator.addViewEvent(2L, 202L);

        assertThat(aggregator.getViewIncrements().get(1L)).isEqualTo(2L);
        assertThat(aggregator.getViewIncrements().get(2L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("isEmpty -- 이벤트 없으면 true")
    void isEmpty_NoEvents_ShouldReturnTrue() {
        assertThat(new CatalogBatchAggregator().isEmpty()).isTrue();
    }
}
