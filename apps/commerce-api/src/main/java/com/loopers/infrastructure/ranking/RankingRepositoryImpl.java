package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<RankedProduct> getTopN(LocalDate date, int page, int size) {
        String key = KEY_PREFIX + date.format(DATE_FORMAT);
        long start = (long) (page - 1) * size;
        long end = start + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        return tuples.stream()
            .filter(t -> t.getValue() != null && t.getScore() != null)
            .map(t -> new RankedProduct(Long.parseLong(t.getValue()), t.getScore()))
            .toList();
    }

    @Override
    public Optional<Integer> getRank(Long productId, LocalDate date) {
        String key = KEY_PREFIX + date.format(DATE_FORMAT);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, productId.toString());
        if (rank == null) {
            return Optional.empty();
        }
        return Optional.of((int) (rank + 1));
    }
}
