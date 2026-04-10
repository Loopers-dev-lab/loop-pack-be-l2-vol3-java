package com.loopers.application.ranking;

import com.loopers.domain.metrics.ProductLikeMetricRepository;
import com.loopers.domain.metrics.ProductOrderMetricRepository;
import com.loopers.domain.metrics.ProductViewMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingAggregator {

    private static final int QUERY_LIMIT = 3000;
    private static final int RANKING_SIZE = 1000;

    private final ProductViewMetricRepository viewMetricRepository;
    private final ProductLikeMetricRepository likeMetricRepository;
    private final ProductOrderMetricRepository orderMetricRepository;
    private final RankingScorer scorer;

    public Map<Long, Double> aggregate(LocalDateTime from, LocalDateTime to) {
        Map<Long, Long> viewCounts = viewMetricRepository.sumByBucketTimeRange(from, to, QUERY_LIMIT);
        Map<Long, Long> likeCounts = likeMetricRepository.sumByBucketTimeRange(from, to, QUERY_LIMIT);
        Map<Long, Long> orderQty = orderMetricRepository.sumQuantityByBucketTimeRange(from, to, QUERY_LIMIT);

        Set<Long> allProductIds = new HashSet<>();
        allProductIds.addAll(viewCounts.keySet());
        allProductIds.addAll(likeCounts.keySet());
        allProductIds.addAll(orderQty.keySet());

        Map<Long, Double> scores = new HashMap<>();
        for (Long pid : allProductIds) {
            double s = scorer.score(
                    viewCounts.getOrDefault(pid, 0L),
                    likeCounts.getOrDefault(pid, 0L),
                    orderQty.getOrDefault(pid, 0L)
            );
            if (s > 0) {
                scores.put(pid, s);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(RANKING_SIZE)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
