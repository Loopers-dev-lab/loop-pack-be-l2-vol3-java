package com.loopers.infrastructure.ranking;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ranking.weight")
public class RankingWeightProperties {

    private double view = 0.1;
    private double like = 0.2;
    private double order = 0.7;
}
