package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductDailySignalRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.ScoreAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingApp {

    private static final double SECONDS_IN_DAY = 86400.0;

    private final RankingRepository rankingRepository;
    private final ScoreAggregator scoreAggregator;
    private final ProductDailySignalRepository productDailySignalRepository;

    public void applyLikeDelta(Long productDbId, int delta, LocalDateTime eventAt) {
        if (delta <= 0) {
            return;
        }
        LocalDate date = eventAt.toLocalDate();
        int hour = eventAt.getHour();
        double score = scoreAggregator.scoreForLike(delta) + tieBreakFraction();
        rankingRepository.incrementScore(date, hour, productDbId, score);
        try {
            productDailySignalRepository.upsertLikeCount(productDbId, date, delta);
        } catch (Exception e) {
            log.warn("[DAILY_SIGNAL] like upsert 실패 — productDbId={}, date={}", productDbId, date, e);
        }
    }

    public void applyViewScore(Long productDbId, LocalDateTime eventAt) {
        LocalDate date = eventAt.toLocalDate();
        int hour = eventAt.getHour();
        double score = scoreAggregator.scoreForView() + tieBreakFraction();
        rankingRepository.incrementScore(date, hour, productDbId, score);
        try {
            productDailySignalRepository.upsertViewCount(productDbId, date, 1);
        } catch (Exception e) {
            log.warn("[DAILY_SIGNAL] view upsert 실패 — productDbId={}, date={}", productDbId, date, e);
        }
    }

    public void applyOrderScore(Long productDbId, BigDecimal price, int quantity, LocalDateTime eventAt) {
        LocalDate date = eventAt.toLocalDate();
        int hour = eventAt.getHour();
        double score = scoreAggregator.scoreForOrder(price, quantity) + tieBreakFraction();
        rankingRepository.incrementScore(date, hour, productDbId, score);
        try {
            BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
            productDailySignalRepository.upsertOrderAmount(productDbId, date, amount);
        } catch (Exception e) {
            log.warn("[DAILY_SIGNAL] order upsert 실패 — productDbId={}, date={}", productDbId, date, e);
        }
    }

    private double tieBreakFraction() {
        int secondOfDay = LocalTime.now().toSecondOfDay();
        return secondOfDay / SECONDS_IN_DAY * 0.001;
    }

    public long carryOver(LocalDate sourceDate, LocalDate destDate, double weight) {
        return rankingRepository.carryOver(sourceDate, destDate, weight);
    }

    public long carryOverHourly(LocalDate sourceDate, int sourceHour, LocalDate destDate, int destHour, double weight) {
        return rankingRepository.carryOverHourly(sourceDate, sourceHour, destDate, destHour, weight);
    }
}
