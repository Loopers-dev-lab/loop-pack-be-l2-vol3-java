package com.loopers.application.ranking;

import com.loopers.infrastructure.ranking.RankingFallbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingFallbackAggregator {

    private static final int QUERY_LIMIT = 3000;
    private static final int RANKING_SIZE = 1000;
    private static final double W_VIEW = 0.1;
    private static final double W_LIKE = 0.2;
    private static final double W_ORDER = 0.7;

    private final RankingFallbackRepository fallbackRepository;

    public Map<Long, Double> aggregate(LocalDateTime from, LocalDateTime to) {
        Map<Long, Long> views = fallbackRepository.sumViewsByRange(from, to, QUERY_LIMIT);
        Map<Long, Long> likes = fallbackRepository.sumLikesByRange(from, to, QUERY_LIMIT);
        Map<Long, Long> salesAmounts = fallbackRepository.sumSalesAmountByRange(from, to, QUERY_LIMIT);

        Set<Long> allIds = new HashSet<>();
        allIds.addAll(views.keySet());
        allIds.addAll(likes.keySet());
        allIds.addAll(salesAmounts.keySet());

        return allIds.stream()
                .map(pid -> Map.entry(pid,
                        W_VIEW * views.getOrDefault(pid, 0L)
                                + W_LIKE * likes.getOrDefault(pid, 0L)
                                + W_ORDER * Math.log10(salesAmounts.getOrDefault(pid, 0L) + 1)))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(RANKING_SIZE)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
