package com.loopers.application.queue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HikariPoolMetricCollector 단위 테스트")
class MetricCollectorTest {

    private MeterRegistry mockRegistry(String activeName, double activeValue, String maxName, double maxValue) {
        MeterRegistry registry = mock(MeterRegistry.class);

        Search activeSearch = mock(Search.class);
        Gauge activeGauge = mock(Gauge.class);
        when(registry.find(activeName)).thenReturn(activeSearch);
        when(activeSearch.gauge()).thenReturn(activeGauge);
        when(activeGauge.value()).thenReturn(activeValue);

        Search maxSearch = mock(Search.class);
        Gauge maxGauge = mock(Gauge.class);
        when(registry.find(maxName)).thenReturn(maxSearch);
        when(maxSearch.gauge()).thenReturn(maxGauge);
        when(maxGauge.value()).thenReturn(maxValue);

        return registry;
    }

    private MeterRegistry mockRegistryWithNull(String activeName, String maxName) {
        MeterRegistry registry = mock(MeterRegistry.class);
        Search activeSearch = mock(Search.class);
        Search maxSearch = mock(Search.class);
        when(registry.find(activeName)).thenReturn(activeSearch);
        when(registry.find(maxName)).thenReturn(maxSearch);
        when(activeSearch.gauge()).thenReturn(null);
        when(maxSearch.gauge()).thenReturn(null);
        return registry;
    }

    @Nested
    @DisplayName("collect()")
    class CollectTest {

        @Test
        @DisplayName("active=8, max=10이면 0.8 반환")
        void collect_returnsRatio() {
            // given
            MeterRegistry registry = mockRegistry(
                    "hikaricp.connections.active", 8.0,
                    "hikaricp.connections.max", 10.0
            );
            HikariPoolMetricCollector collector = new HikariPoolMetricCollector(registry);

            // when
            double result = collector.collect();

            // then
            assertThat(result).isCloseTo(0.8, within(0.001));
        }

        @Test
        @DisplayName("active=40, max=50이면 0.8 반환")
        void collect_largerPool_returnsRatio() {
            // given
            MeterRegistry registry = mockRegistry(
                    "hikaricp.connections.active", 40.0,
                    "hikaricp.connections.max", 50.0
            );
            HikariPoolMetricCollector collector = new HikariPoolMetricCollector(registry);

            // when
            double result = collector.collect();

            // then
            assertThat(result).isCloseTo(0.8, within(0.001));
        }

        @Test
        @DisplayName("active=0이면 0.0 반환")
        void collect_noActive_returnsZero() {
            // given
            MeterRegistry registry = mockRegistry(
                    "hikaricp.connections.active", 0.0,
                    "hikaricp.connections.max", 10.0
            );
            HikariPoolMetricCollector collector = new HikariPoolMetricCollector(registry);

            // when
            double result = collector.collect();

            // then
            assertThat(result).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Gauge가 없으면 0.0 반환")
        void collect_noGauge_returnsZero() {
            // given
            MeterRegistry registry = mockRegistryWithNull(
                    "hikaricp.connections.active",
                    "hikaricp.connections.max"
            );
            HikariPoolMetricCollector collector = new HikariPoolMetricCollector(registry);

            // when
            double result = collector.collect();

            // then
            assertThat(result).isEqualTo(0.0);
        }

        @Test
        @DisplayName("max=0이면 0.0 반환 (division by zero 방지)")
        void collect_maxZero_returnsZero() {
            // given
            MeterRegistry registry = mockRegistry(
                    "hikaricp.connections.active", 5.0,
                    "hikaricp.connections.max", 0.0
            );
            HikariPoolMetricCollector collector = new HikariPoolMetricCollector(registry);

            // when
            double result = collector.collect();

            // then
            assertThat(result).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("name()")
    class NameTest {

        @Test
        @DisplayName("hikari-pool-usage 반환")
        void name_returnsCorrectName() {
            MeterRegistry registry = mock(MeterRegistry.class);
            HikariPoolMetricCollector collector = new HikariPoolMetricCollector(registry);
            assertThat(collector.name()).isEqualTo("hikari-pool-usage");
        }
    }
}
