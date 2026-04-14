package com.loopers.application.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RankingApp {

    private final RankingRepository rankingRepository;
    private final MvProductRankRepository mvProductRankRepository;
    private final RankingProductCache productCache;

    public RankingPageResult getTopN(RankingPeriod period, LocalDate date, long page, long size) {
        long offset = page * size;
        if (period == RankingPeriod.DAILY) {
            return getDailyTopN(date, page, size, offset);
        }
        RankPeriodType type = period == RankingPeriod.WEEKLY ? RankPeriodType.WEEKLY : RankPeriodType.MONTHLY;
        String periodKey = period == RankingPeriod.WEEKLY
                ? RankingKeyGenerator.weeklyPeriodKey(date)
                : RankingKeyGenerator.monthlyPeriodKey(date);
        List<RankingEntry> entries = mvProductRankRepository.findByPeriodKey(type, periodKey, offset, size);
        long totalElements = mvProductRankRepository.countByPeriodKey(type, periodKey);
        java.time.ZonedDateTime lastUpdatedAt = mvProductRankRepository.findLastUpdatedAt(type, periodKey).orElse(null);
        List<RankingInfo> items = enrich(entries, offset);
        return new RankingPageResult(items, page, size, totalElements, lastUpdatedAt);
    }

    public RankingPageResult getTopN(LocalDate date, long page, long size) {
        return getTopN(RankingPeriod.DAILY, date, page, size);
    }

    private RankingPageResult getDailyTopN(LocalDate date, long page, long size, long offset) {
        List<RankingEntry> entries = rankingRepository.findTopN(date, offset, size);
        long totalElements = rankingRepository.countMembers(date);
        List<RankingInfo> items = enrich(entries, offset);
        return new RankingPageResult(items, page, size, totalElements, null);
    }

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
        Map<Long, CachedProductSnapshot> snapshots = productCache.findAllByIds(productDbIds(entries));
        List<RankingInfo> items = new ArrayList<>(entries.size());
        long rank = baseOffset;
        for (RankingEntry entry : entries) {
            rank++;
            items.add(toInfo(entry, rank, snapshots.get(entry.productDbId())));
        }
        return items;
    }

    private List<RankingInfo> enrichByCursor(LocalDate date, List<RankingEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, CachedProductSnapshot> snapshots = productCache.findAllByIds(productDbIds(entries));
        List<RankingInfo> items = new ArrayList<>(entries.size());
        for (RankingEntry entry : entries) {
            Long globalRank = rankingRepository.findRank(date, entry.productDbId()).orElse(null);
            long rank = globalRank != null ? globalRank : 0L;
            items.add(toInfo(entry, rank, snapshots.get(entry.productDbId())));
        }
        return items;
    }

    private List<Long> productDbIds(List<RankingEntry> entries) {
        List<Long> ids = new ArrayList<>(entries.size());
        for (RankingEntry entry : entries) {
            ids.add(entry.productDbId());
        }
        return ids;
    }

    public RankingPageResult getHourlyTopN(LocalDate date, int hour, long page, long size) {
        long offset = page * size;
        List<RankingEntry> entries = rankingRepository.findHourlyTopN(date, hour, offset, size);
        long totalElements = rankingRepository.countHourlyMembers(date, hour);
        List<RankingInfo> items = enrich(entries, offset);
        return new RankingPageResult(items, page, size, totalElements, null);
    }

    public RankingCursorResult getHourlyByCursor(LocalDate date, int hour, Double cursorScore, long size) {
        List<RankingEntry> entries = rankingRepository.findHourlyCursor(date, hour, cursorScore, size);
        List<RankingInfo> items = enrichHourlyCursor(date, hour, entries);
        Double nextCursor = items.isEmpty() ? null : entries.get(entries.size() - 1).score();
        return new RankingCursorResult(items, nextCursor);
    }

    private List<RankingInfo> enrichHourlyCursor(LocalDate date, int hour, List<RankingEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, CachedProductSnapshot> snapshots = productCache.findAllByIds(productDbIds(entries));
        List<RankingInfo> items = new ArrayList<>(entries.size());
        for (RankingEntry entry : entries) {
            Long globalRank = rankingRepository.findHourlyRank(date, hour, entry.productDbId()).orElse(null);
            long rank = globalRank != null ? globalRank : 0L;
            items.add(toInfo(entry, rank, snapshots.get(entry.productDbId())));
        }
        return items;
    }

    private RankingInfo toInfo(RankingEntry entry, long rank, CachedProductSnapshot snapshot) {
        if (snapshot == null || snapshot.deleted()) {
            return new RankingInfo(
                    rank,
                    entry.score(),
                    entry.productDbId(),
                    null,
                    null,
                    null,
                    RankingInfo.STATUS_DISCONTINUED
            );
        }
        return new RankingInfo(
                rank,
                entry.score(),
                snapshot.id(),
                snapshot.productId(),
                snapshot.productName(),
                snapshot.price(),
                RankingInfo.STATUS_ACTIVE
        );
    }
}
