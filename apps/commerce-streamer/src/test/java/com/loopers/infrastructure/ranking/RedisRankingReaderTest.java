package com.loopers.infrastructure.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjDoubleConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RedisRankingReader 단위 테스트")
class RedisRankingReaderTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);

    private RedisRankingReader reader;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        reader = new RedisRankingReader(redisTemplate);
    }

    private Set<ZSetOperations.TypedTuple<String>> makePage(int startId, int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            long id = startId + i;
            ZSetOperations.TypedTuple<String> tuple = ZSetOperations.TypedTuple.of(
                    String.valueOf(id), (double) id);
            tuples.add(tuple);
        }
        return tuples;
    }

    @Nested
    @DisplayName("페이지 단위 청크 순회")
    class PagedTraversal {

        @Test
        @DisplayName("엔트리가 PAGE_SIZE 이하이면 한 번의 ZRANGE 호출로 완료된다")
        void singlePage() {
            // given
            String key = "ranking:2026-04-10";
            Set<ZSetOperations.TypedTuple<String>> page = makePage(1, 10);
            when(zSetOps.rangeWithScores(key, 0, RedisRankingReader.PAGE_SIZE - 1))
                    .thenReturn(page);

            AtomicInteger count = new AtomicInteger();

            // when
            reader.forEachWithScore(key, (id, score) -> count.incrementAndGet());

            // then
            assertThat(count.get()).isEqualTo(10);
            verify(zSetOps, times(1)).rangeWithScores(eq(key), anyLong(), anyLong());
        }

        @Test
        @DisplayName("엔트리가 PAGE_SIZE + 1 개이면 ZRANGE 가 두 번 호출되고 모두 순회된다")
        void twoPages() {
            // given
            String key = "ranking:2026-04-10";
            int total = RedisRankingReader.PAGE_SIZE + 1;
            Set<ZSetOperations.TypedTuple<String>> firstPage = makePage(1, RedisRankingReader.PAGE_SIZE);
            Set<ZSetOperations.TypedTuple<String>> secondPage = makePage(RedisRankingReader.PAGE_SIZE + 1, 1);

            when(zSetOps.rangeWithScores(key, 0, RedisRankingReader.PAGE_SIZE - 1))
                    .thenReturn(firstPage);
            when(zSetOps.rangeWithScores(key, RedisRankingReader.PAGE_SIZE, RedisRankingReader.PAGE_SIZE * 2L - 1))
                    .thenReturn(secondPage);

            List<Long> visited = new ArrayList<>();

            // when
            reader.forEachWithScore(key, (id, score) -> visited.add(id));

            // then
            assertThat(visited).hasSize(total);
            verify(zSetOps, times(2)).rangeWithScores(eq(key), anyLong(), anyLong());
        }

        @Test
        @DisplayName("빈 키에서는 consumer 가 호출되지 않는다")
        void emptyKey() {
            // given
            String key = "ranking:empty";
            when(zSetOps.rangeWithScores(eq(key), anyLong(), anyLong())).thenReturn(Set.of());

            AtomicInteger count = new AtomicInteger();

            // when
            reader.forEachWithScore(key, (id, score) -> count.incrementAndGet());

            // then
            assertThat(count.get()).isZero();
        }

        @Test
        @DisplayName("key 가 null 이면 consumer 가 호출되지 않는다")
        void nullKey() {
            // given
            AtomicInteger count = new AtomicInteger();

            // when
            reader.forEachWithScore(null, (id, score) -> count.incrementAndGet());

            // then
            assertThat(count.get()).isZero();
        }

        @Test
        @DisplayName("잘못된 멤버 값은 skip 되고 나머지는 모두 전달된다")
        void invalidMemberSkipped() {
            // given
            String key = "ranking:2026-04-10";
            Set<ZSetOperations.TypedTuple<String>> page = new LinkedHashSet<>();
            page.add(ZSetOperations.TypedTuple.of("1", 100.0));
            page.add(ZSetOperations.TypedTuple.of("not-a-number", 50.0));
            page.add(ZSetOperations.TypedTuple.of("2", 30.0));
            when(zSetOps.rangeWithScores(key, 0, RedisRankingReader.PAGE_SIZE - 1))
                    .thenReturn(page);

            List<Long> visited = new ArrayList<>();

            // when
            reader.forEachWithScore(key, (id, score) -> visited.add(id));

            // then
            assertThat(visited).containsExactlyInAnyOrder(1L, 2L);
        }
    }
}
