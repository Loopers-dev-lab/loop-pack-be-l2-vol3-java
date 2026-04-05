package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingWeightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class RankingApp {

    private final RankingRepository rankingRepository;
    private final RankingWeightProperties weightProperties;

    public void applyLikeDelta(Long productDbId, int delta, LocalDate date) {
        double score = weightProperties.like() * delta;
        rankingRepository.incrementScore(date, productDbId, score);
    }

    public void applyOrderScore(Long productDbId, BigDecimal price, int quantity, LocalDate date) {
        double rawScore = price.doubleValue() * quantity;
        double score = weightProperties.order() * rawScore;
        rankingRepository.incrementScore(date, productDbId, score);
    }
}
