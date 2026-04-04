package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<ProductRanking> getTopN(String key, long start, long stop) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, start, stop);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductRanking> rankings = new ArrayList<>();
        long rank = start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            rankings.add(new ProductRanking(
                Long.parseLong(tuple.getValue()),
                tuple.getScore() != null ? tuple.getScore() : 0.0,
                rank++
            ));
        }
        return rankings;
    }

    @Override
    public Optional<Long> getRank(String key, Long productId) {
        Long zeroBasedRank = redisTemplate.opsForZSet().reverseRank(key, productId.toString());
        if (zeroBasedRank == null) {
            return Optional.empty();
        }
        return Optional.of(zeroBasedRank + 1);
    }

    @Override
    public long getTotalCount(String key) {
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }
}
