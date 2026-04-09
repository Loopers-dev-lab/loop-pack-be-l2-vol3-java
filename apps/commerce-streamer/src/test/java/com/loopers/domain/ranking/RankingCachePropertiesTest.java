package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingCacheProperties 바인딩 테스트")
class RankingCachePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(RankingCacheProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Nested
    @DisplayName("유효한 retention")
    class Valid {

        @Test
        @DisplayName("P2D 는 정상 바인딩된다")
        void p2d_bindsSuccessfully() {
            // given & when & then
            runner.withPropertyValues("ranking.cache.retention=P2D")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        RankingCacheProperties props = context.getBean(RankingCacheProperties.class);
                        assertThat(props.retention().toDays()).isEqualTo(2);
                    });
        }
    }

    @Nested
    @DisplayName("유효하지 않은 retention")
    class Invalid {

        @Test
        @DisplayName("PT0S (0초) 는 컨텍스트 로딩에 실패한다")
        void zeroRetention_failsToLoad() {
            // given & when & then
            runner.withPropertyValues("ranking.cache.retention=PT0S")
                    .run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("-PT1S (음수) 는 컨텍스트 로딩에 실패한다")
        void negativeRetention_failsToLoad() {
            // given & when & then
            runner.withPropertyValues("ranking.cache.retention=-PT1S")
                    .run(context -> assertThat(context).hasFailed());
        }

        @Test
        @DisplayName("retention 이 누락되면 컨텍스트 로딩에 실패한다")
        void missingRetention_failsToLoad() {
            // given & when & then
            runner.run(context -> assertThat(context).hasFailed());
        }
    }
}
