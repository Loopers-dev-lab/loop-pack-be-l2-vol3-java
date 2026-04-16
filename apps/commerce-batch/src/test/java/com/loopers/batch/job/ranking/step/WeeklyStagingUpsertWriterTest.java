package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.dto.StagingDelta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyStagingUpsertWriterTest {

    @Test
    @DisplayName("같은 product_id 의 여러 row 가 하나의 엔트리로 합산된다.")
    void aggregatesDuplicatesByProductId() {
        List<StagingDelta> items = List.of(
            new StagingDelta(1L, 2.5),
            new StagingDelta(2L, 1.0),
            new StagingDelta(1L, 3.0),
            new StagingDelta(1L, 0.5),
            new StagingDelta(2L, 4.0)
        );

        Map<Long, Double> aggregated = WeeklyStagingUpsertWriter.aggregateByProductId(items);

        assertThat(aggregated).hasSize(2);
        assertThat(aggregated.get(1L)).isEqualTo(6.0);
        assertThat(aggregated.get(2L)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("빈 입력은 빈 Map 을 반환한다.")
    void emptyInputReturnsEmptyMap() {
        Map<Long, Double> aggregated = WeeklyStagingUpsertWriter.aggregateByProductId(List.of());
        assertThat(aggregated).isEmpty();
    }

    @Test
    @DisplayName("중복 없는 입력은 그대로 각 PK 당 1개 엔트리를 만든다.")
    void uniqueKeysPassThrough() {
        List<StagingDelta> items = List.of(
            new StagingDelta(10L, 1.0),
            new StagingDelta(20L, 2.0),
            new StagingDelta(30L, 3.0)
        );

        Map<Long, Double> aggregated = WeeklyStagingUpsertWriter.aggregateByProductId(items);

        assertThat(aggregated).containsExactlyInAnyOrderEntriesOf(
            Map.of(10L, 1.0, 20L, 2.0, 30L, 3.0));
    }

    @Test
    @DisplayName("첫 등장 순서가 LinkedHashMap 덕분에 보존된다.")
    void preservesInsertionOrder() {
        List<StagingDelta> items = List.of(
            new StagingDelta(30L, 1.0),
            new StagingDelta(10L, 1.0),
            new StagingDelta(20L, 1.0),
            new StagingDelta(30L, 1.0) // 중복 — 30 이 가장 먼저 등장
        );

        Map<Long, Double> aggregated = WeeklyStagingUpsertWriter.aggregateByProductId(items);

        assertThat(aggregated.keySet()).containsExactly(30L, 10L, 20L);
    }
}
