package com.loopers.application.ranking;

import com.loopers.domain.ranking.WeightConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WeightConfigServiceIntegrationTest {

    @Autowired
    private WeightConfigService weightConfigService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 그룹_생성 {

        @Test
        void 유효한_정보로_생성하면_활성_상태로_생성된다() {
            WeightConfig result = weightConfigService.create("experiment", 0.5, 0.3, 0.2, 50);

            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getGroupName()).isEqualTo("experiment"),
                    () -> assertThat(result.getWView()).isEqualTo(0.5),
                    () -> assertThat(result.isActive()).isTrue()
            );
        }

        @Test
        void 이미_존재하는_그룹명으로_생성하면_예외() {
            weightConfigService.create("experiment", 0.5, 0.3, 0.2, 50);

            assertThatThrownBy(() -> weightConfigService.create("experiment", 0.1, 0.2, 0.7, 30))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    class 그룹_수정 {

        @Test
        void 존재하는_그룹의_가중치를_수정하면_성공한다() {
            weightConfigService.create("experiment", 0.5, 0.3, 0.2, 50);

            WeightConfig result = weightConfigService.update("experiment", 0.1, 0.5, 0.4, 30);

            assertAll(
                    () -> assertThat(result.getWView()).isEqualTo(0.1),
                    () -> assertThat(result.getWLike()).isEqualTo(0.5),
                    () -> assertThat(result.getWOrder()).isEqualTo(0.4),
                    () -> assertThat(result.getTrafficPct()).isEqualTo(30)
            );
        }

        @Test
        void 존재하지_않는_그룹을_수정하면_예외() {
            assertThatThrownBy(() -> weightConfigService.update("nonexistent", 0.1, 0.2, 0.7, 100))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    class 그룹_비활성화 {

        @Test
        void 실험_그룹을_비활성화하면_성공한다() {
            weightConfigService.create("experiment", 0.5, 0.3, 0.2, 50);

            weightConfigService.deactivate("experiment");

            List<WeightConfig> activeConfigs = weightConfigService.getAllActive();
            assertThat(activeConfigs).noneMatch(c -> "experiment".equals(c.getGroupName()));
        }

        @Test
        void control_그룹을_비활성화하면_예외() {
            assertThatThrownBy(() -> weightConfigService.deactivate("control"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 존재하지_않는_그룹을_비활성화하면_예외() {
            assertThatThrownBy(() -> weightConfigService.deactivate("nonexistent"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    class 활성_그룹_조회 {

        @Test
        void 활성_그룹만_반환한다() {
            weightConfigService.create("control", 0.1, 0.2, 0.7, 50);
            weightConfigService.create("experiment", 0.5, 0.3, 0.2, 50);
            weightConfigService.deactivate("experiment");

            List<WeightConfig> result = weightConfigService.getAllActive();

            assertAll(
                    () -> assertThat(result).hasSize(1),
                    () -> assertThat(result.get(0).getGroupName()).isEqualTo("control")
            );
        }
    }
}
