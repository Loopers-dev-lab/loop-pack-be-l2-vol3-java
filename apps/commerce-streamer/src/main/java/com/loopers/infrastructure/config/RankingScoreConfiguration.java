package com.loopers.infrastructure.config;

import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.domain.ranking.RankingScoreWeights;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RankingScoreConfiguration {

    /**
     * 가중치를 초기화한다.
     * @return 가중치
     */
    @Bean
    public RankingScoreWeights rankingScoreWeights() {
        return RankingScoreWeights.questExample();
    }

    /**
     * 계산기를 초기화한다.
     * @param rankingScoreWeights 가중치
     * @return 계산기 (조회 * 가중치 + 좋아요 * 가중치 + 판매 * 가중치)
     */
    @Bean
    public RankingScoreCalculator rankingScoreCalculator(RankingScoreWeights rankingScoreWeights) {
        return new RankingScoreCalculator(rankingScoreWeights);
    }
}
