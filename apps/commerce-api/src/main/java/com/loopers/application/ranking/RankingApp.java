package com.loopers.application.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
public class RankingApp {

    private final RankingRepository rankingRepository;
    private final MvProductRankRepository mvProductRankRepository;
    private final RankingProductCache productCache;

    @Value("${ranking.cold-start-fallback.enabled:false}")
    private boolean coldStartFallbackEnabled;

    public RankingPageResult getTopN(RankingPeriod period, LocalDate date, long page, long size) {
        long offset = page * size;
        if (period == RankingPeriod.DAILY) {
            return getDailyTopN(date, page, size, offset);
        }
        RankPeriodType type = (period == RankingPeriod.WEEKLY) ? RankPeriodType.WEEKLY : RankPeriodType.MONTHLY;
        String currentKey = periodKey(period, date, false);
        RankingPageResult primary = queryMvPeriod(type, currentKey, page, size, offset, false);
        if (primary.totalElements() > 0 || !coldStartFallbackEnabled) {
            return primary;
        }
        return queryMvPeriod(type, periodKey(period, date, true), page, size, offset, true);
    }

    public RankingPageResult getTopN(LocalDate date, long page, long size) {
        return getTopN(RankingPeriod.DAILY, date, page, size);
    }

    public RankingCursorResult getByCursor(LocalDate date, Double cursorScore, long size) {
        List<RankingEntry> entries = rankingRepository.findByCursor(date, cursorScore, size);
        List<RankingInfo> items = enrichWith(entries,
                (i, e) -> rankingRepository.findRank(date, e.productDbId()).orElse(0L));
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

    public RankingPageResult getHourlyTopN(LocalDate date, int hour, long page, long size) {
        long offset = page * size;
        List<RankingEntry> entries = rankingRepository.findHourlyTopN(date, hour, offset, size);
        long totalElements = rankingRepository.countHourlyMembers(date, hour);
        List<RankingInfo> items = enrichByOffset(entries, offset);
        return new RankingPageResult(items, page, size, totalElements, null);
    }

    public RankingCursorResult getHourlyByCursor(LocalDate date, int hour, Double cursorScore, long size) {
        List<RankingEntry> entries = rankingRepository.findHourlyCursor(date, hour, cursorScore, size);
        List<RankingInfo> items = enrichWith(entries,
                (i, e) -> rankingRepository.findHourlyRank(date, hour, e.productDbId()).orElse(0L));
        Double nextCursor = items.isEmpty() ? null : entries.get(entries.size() - 1).score();
        return new RankingCursorResult(items, nextCursor);
    }

    private String periodKey(RankingPeriod period, LocalDate date, boolean previous) {
        if (period == RankingPeriod.WEEKLY) {
            return previous ? RankingKeyGenerator.previousWeeklyPeriodKey(date)
                    : RankingKeyGenerator.weeklyPeriodKey(date);
        }
        return previous ? RankingKeyGenerator.previousMonthlyPeriodKey(date)
                : RankingKeyGenerator.monthlyPeriodKey(date);
    }

    private RankingPageResult queryMvPeriod(RankPeriodType type, String periodKey, long page, long size, long offset, boolean isFallback) {
        List<RankingEntry> entries = mvProductRankRepository.findByPeriodKey(type, periodKey, offset, size);
        long totalElements = mvProductRankRepository.countByPeriodKey(type, periodKey);
        java.time.ZonedDateTime lastUpdatedAt = mvProductRankRepository.findLastUpdatedAt(type, periodKey).orElse(null);
        Long publishedVersion = mvProductRankRepository.findPublishedVersion(type, periodKey).orElse(null);
        List<RankingInfo> items = enrichByOffset(entries, offset);
        return new RankingPageResult(items, page, size, totalElements, lastUpdatedAt, periodKey, isFallback, publishedVersion);
    }

    private RankingPageResult getDailyTopN(LocalDate date, long page, long size, long offset) {
        List<RankingEntry> entries = rankingRepository.findTopN(date, offset, size);
        long totalElements = rankingRepository.countMembers(date);
        List<RankingInfo> items = enrichByOffset(entries, offset);
        return new RankingPageResult(items, page, size, totalElements, null);
    }

    private List<RankingInfo> enrichByOffset(List<RankingEntry> entries, long baseOffset) {
        return enrichWith(entries, (i, e) -> baseOffset + i + 1);
    }

    private List<RankingInfo> enrichWith(List<RankingEntry> entries, BiFunction<Integer, RankingEntry, Long> rankResolver) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Long> ids = entries.stream().map(RankingEntry::productDbId).toList();
        Map<Long, CachedProductSnapshot> snapshots = productCache.findAllByIds(ids);
        List<RankingInfo> items = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry entry = entries.get(i);
            items.add(toInfo(entry, rankResolver.apply(i, entry), snapshots.get(entry.productDbId())));
        }
        return items;
    }

    private RankingInfo toInfo(RankingEntry entry, long rank, CachedProductSnapshot snapshot) {
        if (snapshot == null || snapshot.deleted()) {
            return new RankingInfo(rank, entry.score(), entry.productDbId(),
                    null, null, null, RankingInfo.STATUS_DISCONTINUED);
        }
        return new RankingInfo(rank, entry.score(), snapshot.id(),
                snapshot.productId(), snapshot.productName(), snapshot.price(),
                RankingInfo.STATUS_ACTIVE);
    }
}
