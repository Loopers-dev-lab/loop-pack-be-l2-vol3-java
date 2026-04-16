package com.loopers.ranking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "ranking.weight", name = "view")
@EnableConfigurationProperties(RankingWeightProperties.class)
public class RankingAutoConfiguration {

    @Bean
    public ScoreCalculator scoreCalculator(RankingWeightProperties properties) {
        return new ScoreCalculator(properties);
    }
}
