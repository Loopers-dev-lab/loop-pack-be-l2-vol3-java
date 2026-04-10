package com.loopers.application.ranking;

import com.loopers.domain.ranking.WeightConfig;
import com.loopers.domain.ranking.WeightConfigRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExperimentGroupResolverTest {

    @Nested
    class 그룹_분배 {

        @Test
        void userId가_null이면_control을_반환한다() {
            ExperimentGroupResolver resolver = resolverWith(List.of());

            assertThat(resolver.resolve(null)).isEqualTo("control");
        }

        @Test
        void 활성_그룹이_1개뿐이면_control을_반환한다() {
            ExperimentGroupResolver resolver = resolverWith(
                    List.of(WeightConfig.create("control", 0.1, 0.2, 0.7, 100))
            );

            assertThat(resolver.resolve(42L)).isEqualTo("control");
        }

        @Test
        void 실험_그룹이_있으면_트래픽_비율에_따라_분배한다() {
            WeightConfig control = WeightConfig.create("control", 0.1, 0.2, 0.7, 50);
            WeightConfig experiment = WeightConfig.create("experiment", 0.5, 0.3, 0.2, 50);
            ExperimentGroupResolver resolver = resolverWith(List.of(control, experiment));

            int experimentCount = 0;
            for (long userId = 1; userId <= 1000; userId++) {
                if ("experiment".equals(resolver.resolve(userId))) {
                    experimentCount++;
                }
            }

            // 50% 트래픽이므로 대략 400~600 사이
            assertThat(experimentCount).isBetween(350, 650);
        }

        @Test
        void 같은_userId는_항상_같은_그룹을_반환한다() {
            WeightConfig control = WeightConfig.create("control", 0.1, 0.2, 0.7, 50);
            WeightConfig experiment = WeightConfig.create("experiment", 0.5, 0.3, 0.2, 50);
            ExperimentGroupResolver resolver = resolverWith(List.of(control, experiment));

            String firstResult = resolver.resolve(42L);

            for (int i = 0; i < 100; i++) {
                assertThat(resolver.resolve(42L)).isEqualTo(firstResult);
            }
        }

        @Test
        void 트래픽_범위_밖이면_control을_반환한다() {
            WeightConfig control = WeightConfig.create("control", 0.1, 0.2, 0.7, 90);
            WeightConfig experiment = WeightConfig.create("experiment", 0.5, 0.3, 0.2, 10);
            ExperimentGroupResolver resolver = resolverWith(List.of(control, experiment));

            // bucket >= 10인 userId는 control로 분배됨
            int controlCount = 0;
            for (long userId = 1; userId <= 1000; userId++) {
                if ("control".equals(resolver.resolve(userId))) {
                    controlCount++;
                }
            }

            // 90% 트래픽이므로 대략 850~950 사이
            assertThat(controlCount).isBetween(800, 980);
        }
    }

    private ExperimentGroupResolver resolverWith(List<WeightConfig> configs) {
        return new ExperimentGroupResolver(new StubWeightConfigRepository(configs));
    }

    private static class StubWeightConfigRepository implements WeightConfigRepository {
        private final List<WeightConfig> configs;

        StubWeightConfigRepository(List<WeightConfig> configs) {
            this.configs = configs;
        }

        @Override
        public List<WeightConfig> findAllByActiveTrue() {
            return configs;
        }

        @Override
        public Optional<WeightConfig> findByGroupName(String groupName) {
            return configs.stream()
                    .filter(c -> c.getGroupName().equals(groupName))
                    .findFirst();
        }

        @Override
        public WeightConfig save(WeightConfig config) {
            return config;
        }
    }
}
