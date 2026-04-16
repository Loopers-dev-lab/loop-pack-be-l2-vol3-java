package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RankingRepositoryImpl implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    // Replica 우선 읽기 (Primary Bean — ReadFrom.REPLICA_PREFERRED)
    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final MvWeeklyRankJpaRepository mvWeeklyRankJpaRepository;
    private final MvMonthlyRankJpaRepository mvMonthlyRankJpaRepository;

    // ---- DAILY ----

    @Override
    public List<RankingEntry> getTopRankings(LocalDate date, long start, long end) {
        String key = buildKey(date);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                defaultRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        return tuples.stream()
                .map(t -> new RankingEntry(
                        Long.parseLong(t.getValue()),
                        t.getScore() != null ? t.getScore() : 0))
                .toList();
    }

    @Override
    public Long getRank(LocalDate date, Long productId) {
        String key = buildKey(date);
        return defaultRedisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
    }

    @Override
    public long getTotalCount(LocalDate date) {
        String key = buildKey(date);
        Long size = defaultRedisTemplate.opsForZSet().size(key);
        return size != null ? size : 0;
    }

    @Override
    public List<RankingEntry> getHourlyTopRankings(String hourKey, long start, long end) {
        String key = "ranking:hourly:" + hourKey;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                defaultRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        return tuples.stream()
                .map(t -> new RankingEntry(
                        Long.parseLong(t.getValue()),
                        t.getScore() != null ? t.getScore() : 0))
                .toList();
    }

    // ---- WEEKLY ----

    @Override
    public List<MvRankingEntry> getWeeklyTop(LocalDate baseDate, int page, int size) {
        return mvWeeklyRankJpaRepository
                .findByBaseDateOrderByRankNoAsc(baseDate, PageRequest.of(page, size))
                .stream()
                .map(m -> new MvRankingEntry(m.getProductId(), m.getRankNo(), m.getScore()))
                .toList();
    }

    @Override
    public long getWeeklyTotal(LocalDate baseDate) {
        return mvWeeklyRankJpaRepository.countByBaseDate(baseDate);
    }

    @Override
    public Optional<LocalDateTime> getWeeklyAggregatedAt(LocalDate baseDate) {
        return mvWeeklyRankJpaRepository.findAggregatedAt(baseDate);
    }

    // ---- MONTHLY ----

    @Override
    public List<MvRankingEntry> getMonthlyTop(YearMonth yearMonth, int page, int size) {
        return mvMonthlyRankJpaRepository
                .findByYearMonthOrderByRankNoAsc(formatYearMonth(yearMonth), PageRequest.of(page, size))
                .stream()
                .map(m -> new MvRankingEntry(m.getProductId(), m.getRankNo(), m.getScore()))
                .toList();
    }

    @Override
    public long getMonthlyTotal(YearMonth yearMonth) {
        return mvMonthlyRankJpaRepository.countByYearMonth(formatYearMonth(yearMonth));
    }

    @Override
    public Optional<LocalDateTime> getMonthlyAggregatedAt(YearMonth yearMonth) {
        return mvMonthlyRankJpaRepository.findAggregatedAt(formatYearMonth(yearMonth));
    }

    private String buildKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FORMAT);
    }

    private String formatYearMonth(YearMonth yearMonth) {
        return yearMonth.format(YEAR_MONTH_FORMAT);
    }
}
