package com.loopers.application.queue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HikariPoolMetricCollector implements MetricCollector {

    private final MeterRegistry meterRegistry;

    @Override
    public String name() {
        return "hikari-pool-usage";
    }

    @Override
    public double collect() {
        Gauge activeGauge = meterRegistry.find("hikaricp.connections.active").gauge();
        Gauge maxGauge = meterRegistry.find("hikaricp.connections.max").gauge();

        if (activeGauge == null || maxGauge == null) {
            return 0.0;
        }

        double active = activeGauge.value();
        double max = maxGauge.value();

        if (max <= 0) {
            return 0.0;
        }

        return active / max;
    }
}
