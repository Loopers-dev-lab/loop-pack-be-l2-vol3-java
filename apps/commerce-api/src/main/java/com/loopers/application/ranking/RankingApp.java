package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RankingApp {

    private final RankingRepository rankingRepository;
    private final RankingProductCache productCache;

    @Cacheable(value = "rankingPage", key = "#date.toString() + ':' + #page + ':' + #size")
    public RankingPageResult getTopN(LocalDate date, long page, long size) {
        long offset = page * size;
        List<RankingEntry> entries = rankingRepository.findTopN(date, offset, size);
        long totalElements = rankingRepository.countMembers(date);
        List<RankingInfo> items = enrich(entries, offset);
        return new RankingPageResult(items, page, size, totalElements);
    }

    @Cacheable(value = "rankingPage", key = "#date.toString() + ':cursor:' + (#cursorScore != null ? #cursorScore : 'top') + ':' + #size")
    public RankingCursorResult getByCursor(LocalDate date, Double cursorScore, long size) {
        List<RankingEntry> entries = rankingRepository.findByCursor(date, cursorScore, size);
        List<RankingInfo> items = enrichByCursor(date, entries);
        Double nextCursor = items.isEmpty() ? null : entries.get(entries.size() - 1).score();
        return new RankingCursorResult(items, nextCursor);
    }

    public Optional<ProductRankingInfo> getProductRanking(Long productDbId, LocalDate date) {
        Optional<Long> rank = rankingRepository.findRank(date, productDbId);
        if (rank.isEmpty()) {
            return Optional.empty();
        }
        Double score = rankingRepository.findScore(date, productDbId).orElse(null);
        return Optional.of(new ProductRankingInfo(rank.get(), score));
    }

    private List<RankingInfo> enrich(List<RankingEntry> entries, long baseOffset) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<RankingInfo> items = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry entry = entries.get(i);
            long rank = baseOffset + i + 1;
            CachedProductSnapshot snapshot = productCache.findById(entry.productDbId());
            items.add(toInfo(entry, rank, snapshot));
        }
        return items;
    }

    private List<RankingInfo> enrichByCursor(LocalDate date, List<RankingEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<RankingInfo> items = new ArrayList<>(entries.size());
        for (RankingEntry entry : entries) {
            Long globalRank = rankingRepository.findRank(date, entry.productDbId()).orElse(null);
            long rank = globalRank != null ? globalRank : 0L;
            CachedProductSnapshot snapshot = productCache.findById(entry.productDbId());
            items.add(toInfo(entry, rank, snapshot));
        }
        return items;
    }

    private RankingInfo toInfo(RankingEntry entry, long rank, CachedProductSnapshot snapshot) {
        if (snapshot == null) {
            return new RankingInfo(rank, entry.score(), entry.productDbId(),
                    null, "(삭제된 상품)", null, RankingInfo.STATUS_DISCONTINUED);
        }
        String status = snapshot.deleted() ? RankingInfo.STATUS_DISCONTINUED : RankingInfo.STATUS_ACTIVE;
        return new RankingInfo(
                rank,
                entry.score(),
                snapshot.id(),
                snapshot.productId(),
                snapshot.productName(),
                snapshot.price(),
                status
        );
    }
}
