package com.loopers.domain.rank;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class RankScoreCalculator {

    private final RankingWeightProperties weights;

    public double calculate(long viewCount, long likeCount, BigDecimal orderAmount) {
        return weights.view() * viewCount
                + weights.like() * likeCount
                + weights.order() * orderAmount.doubleValue();
    }
}
