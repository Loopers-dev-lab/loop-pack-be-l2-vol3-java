package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingWeights 바인딩 테스트")
class RankingWeightsTest {

    @Configuration
    @EnableConfigurationProperties(RankingWeights.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Nested
    @DisplayName("유효한 가중치")
    class Valid {

        @Test
        @DisplayName("0.1/0.2/0.7 은 정상 바인딩된다")
        void validWeights_bindSuccessfully() {
            // given & when & then
            runner.withPropertyValues(
                            "ranking.weights.view=0.1",
                            "ranking.weights.like=0.2",
                            "ranking.weights.order=0.7")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        RankingWeights weights = context.getBean(RankingWeights.class);
                        assertThat(weights.view()).isEqualTo(0.1);
                        assertThat(weights.like()).isEqualTo(0.2);
                        assertThat(weights.order()).isEqualTo(0.7);
                    });
        }
    }

    @Nested
    @DisplayName("유효하지 않은 가중치")
    class Invalid {

        @Test
        @DisplayName("음수 가중치는 컨텍스트 로딩에 실패한다")
        void negativeWeight_failsToLoad() {
            // given & when & then
            runner.withPropertyValues(
                            "ranking.weights.view=-0.1",
                            "ranking.weights.like=0.2",
                            "ranking.weights.order=0.7")
                    .run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("NaN 가중치는 컨텍스트 로딩에 실패한다")
        void nanWeight_failsToLoad() {
            // given & when & then
            runner.withPropertyValues(
                            "ranking.weights.view=NaN",
                            "ranking.weights.like=0.2",
                            "ranking.weights.order=0.7")
                    .run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("Infinity 가중치는 컨텍스트 로딩에 실패한다")
        void infinityWeight_failsToLoad() {
            // given & when & then
            runner.withPropertyValues(
                            "ranking.weights.view=Infinity",
                            "ranking.weights.like=0.2",
                            "ranking.weights.order=0.7")
                    .run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("총합이 0이면 컨텍스트 로딩에 실패한다")
        void zeroSumWeights_failsToLoad() {
            // given & when & then
            runner.withPropertyValues(
                            "ranking.weights.view=0",
                            "ranking.weights.like=0",
                            "ranking.weights.order=0")
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
