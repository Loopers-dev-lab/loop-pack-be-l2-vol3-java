package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.Pageable;

public class FakeProductRankingScoreRepository implements ProductRankingScoreRepository {

    private final Map<Long, ProductRankingScoreModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public ProductRankingScoreModel save(ProductRankingScoreModel score) {
        if (score.getId() == 0L) {
            try {
                var idField = score.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(score, idGenerator.getAndIncrement());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        store.put(score.getId(), score);
        return score;
    }

    @Override
    public Optional<ProductRankingScoreModel> findByProductIdAndRankingDate(
            Long productId, LocalDate rankingDate) {
        return store.values().stream()
                .filter(s -> s.getProductId().equals(productId))
                .filter(s -> s.getRankingDate().equals(rankingDate))
                .findFirst();
    }

    @Override
    public List<ProductRankingScoreModel> findAllByRankingDate(LocalDate rankingDate) {
        return store.values().stream()
                .filter(s -> s.getRankingDate().equals(rankingDate))
                .toList();
    }

    @Override
    public List<ProductRankingScoreModel> findTopByRankingDateOrderByScoreDesc(
            LocalDate rankingDate, Pageable pageable) {
        return store.values().stream()
                .filter(s -> s.getRankingDate().equals(rankingDate))
                .sorted(Comparator.comparingDouble(ProductRankingScoreModel::getScore).reversed())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .toList();
    }

    @Override
    public long countByRankingDate(LocalDate rankingDate) {
        return store.values().stream()
                .filter(s -> s.getRankingDate().equals(rankingDate))
                .count();
    }

    @Override
    public Optional<Long> findRankByProductIdAndRankingDate(
            Long productId, LocalDate rankingDate) {
        List<ProductRankingScoreModel> sorted = store.values().stream()
                .filter(s -> s.getRankingDate().equals(rankingDate))
                .sorted(Comparator.comparingDouble(ProductRankingScoreModel::getScore).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getProductId().equals(productId)) {
                long rank = 1;
                for (int j = 0; j < i; j++) {
                    if (sorted.get(j).getScore() != sorted.get(i).getScore()) {
                        rank = j + 1;
                    }
                }
                if (i > 0 && sorted.get(i - 1).getScore() == sorted.get(i).getScore()) {
                    rank = i; // same rank as previous
                } else {
                    rank = i + 1;
                }
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }
}
