package com.loopers.domain.ranking;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ranking.weights")
@Getter
@Setter
public class RankingWeightProperties {
    private double view = 0.01;
    private double like = 0.3;
    private double orderBase = 1.0;
    private double priceEpsilon = 0.01;

    public double orderScore(long amount) {
        return orderBase + priceEpsilon * Math.log10(amount + 1);
    }
}
