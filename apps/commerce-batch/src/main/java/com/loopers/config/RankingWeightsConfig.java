package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 랭킹 점수 가중치 설정.
 *
 * application.yml 의 ranking.weights 프리픽스에서 값을 바인딩한다.
 *
 * 점수 계산 공식:
 *   score = view_count * view + like_count * like + order_count * order
 *
 * 가중치는 합산이 1.0 일 필요는 없으나 모두 0 이어서는 안 된다.
 * 일반적으로 구매 전환이 가장 중요하므로 order 가중치를 높게 설정한다.
 */
@Configuration
@EnableConfigurationProperties(RankingWeightsConfig.RankingWeights.class)
public class RankingWeightsConfig {

    /**
     * 랭킹 점수 가중치 불변 VO.
     *
     * @param view   조회수 가중치 (기본값 0.1)
     * @param like   좋아요수 가중치 (기본값 0.2)
     * @param order  주문수 가중치 (기본값 0.7)
     */
    @ConfigurationProperties(prefix = "ranking.weights")
    public record RankingWeights(double view, double like, double order) {
        public RankingWeights {
            // 음수 가중치는 점수를 역전시키므로 허용하지 않는다
            if (view < 0 || like < 0 || order < 0) {
                throw new IllegalArgumentException("ranking.weights must be non-negative");
            }
            // 모든 가중치가 0 이면 점수가 항상 0 이어서 랭킹 정렬이 의미 없다
            if (view + like + order <= 0) {
                throw new IllegalArgumentException("ranking.weights sum must be positive");
            }
        }
    }
}
