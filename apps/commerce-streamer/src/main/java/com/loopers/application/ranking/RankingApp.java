package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingWeightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class RankingApp {

    private static final double SECONDS_IN_DAY = 86400.0;

    private final RankingRepository rankingRepository;
    private final RankingWeightProperties weightProperties;

    public void applyLikeDelta(Long productDbId, int delta, LocalDate date) {
        if (delta <= 0) {
            return;
        }
        double score = weightProperties.like() * delta + tieBreakFraction();
        rankingRepository.incrementScore(date, productDbId, score);
    }

    public void applyViewScore(Long productDbId, LocalDate date) {
        double score = weightProperties.view() + tieBreakFraction();
        rankingRepository.incrementScore(date, productDbId, score);
    }

    public void applyOrderScore(Long productDbId, BigDecimal price, int quantity, LocalDate date) {
        double rawScore = price.doubleValue() * quantity;
        double score = weightProperties.order() * rawScore + tieBreakFraction();
        rankingRepository.incrementScore(date, productDbId, score);
    }

    private double tieBreakFraction() {
        int secondOfDay = LocalTime.now().toSecondOfDay();
        return secondOfDay / SECONDS_IN_DAY * 0.001;
    }

    public long carryOver(LocalDate sourceDate, LocalDate destDate, double weight) {
        return rankingRepository.carryOver(sourceDate, destDate, weight);
    }
}
