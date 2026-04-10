package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WeightConfigTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값으로_생성하면_활성_상태로_생성된다() {
            WeightConfig config = WeightConfig.create("experiment", 0.5, 0.3, 0.2, 50);

            assertAll(
                    () -> assertThat(config.getGroupName()).isEqualTo("experiment"),
                    () -> assertThat(config.getWView()).isEqualTo(0.5),
                    () -> assertThat(config.getWLike()).isEqualTo(0.3),
                    () -> assertThat(config.getWOrder()).isEqualTo(0.2),
                    () -> assertThat(config.getTrafficPct()).isEqualTo(50),
                    () -> assertThat(config.isActive()).isTrue()
            );
        }
    }

    @Nested
    class 기본_설정 {

        @Test
        void 기본_설정은_control_그룹에_기본_가중치를_가진다() {
            WeightConfig config = WeightConfig.defaultConfig();

            assertAll(
                    () -> assertThat(config.getGroupName()).isEqualTo("control"),
                    () -> assertThat(config.getWView()).isEqualTo(0.1),
                    () -> assertThat(config.getWLike()).isEqualTo(0.2),
                    () -> assertThat(config.getWOrder()).isEqualTo(0.7),
                    () -> assertThat(config.getTrafficPct()).isEqualTo(100),
                    () -> assertThat(config.isActive()).isTrue()
            );
        }
    }

    @Nested
    class 가중치_수정 {

        @Test
        void 가중치와_트래픽을_수정하면_값이_변경된다() {
            WeightConfig config = WeightConfig.create("experiment", 0.1, 0.2, 0.7, 100);

            config.updateWeights(0.5, 0.3, 0.2, 50);

            assertAll(
                    () -> assertThat(config.getWView()).isEqualTo(0.5),
                    () -> assertThat(config.getWLike()).isEqualTo(0.3),
                    () -> assertThat(config.getWOrder()).isEqualTo(0.2),
                    () -> assertThat(config.getTrafficPct()).isEqualTo(50)
            );
        }
    }

    @Nested
    class 비활성화 {

        @Test
        void 비활성화하면_활성_상태가_false가_된다() {
            WeightConfig config = WeightConfig.create("experiment", 0.1, 0.2, 0.7, 50);

            config.deactivate();

            assertThat(config.isActive()).isFalse();
        }
    }
}
