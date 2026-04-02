package com.loopers.application.queue;

import com.loopers.config.RateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class RateLimitModeEvaluator {

    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong lastTransitionMs = new AtomicLong(0);

    public RateLimitModeEvaluator(RateLimitProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${queue.rate-limit.evaluation-interval-ms}")
    public void evaluate() {
        if (!properties.enabled()) {
            return;
        }

        double usage = getTomcatThreadUsage();

        if (!active.get() && usage >= properties.activateThreshold()) {
            active.set(true);
            lastTransitionMs.set(System.currentTimeMillis());
            log.warn("[RATE_LIMIT] ACTIVATED — Tomcat thread usage: {}", String.format("%.1f%%", usage * 100));
        } else if (active.get() && usage < properties.deactivateThreshold()) {
            long elapsed = System.currentTimeMillis() - lastTransitionMs.get();
            if (elapsed >= properties.cooldownSeconds() * 1000L) {
                active.set(false);
                lastTransitionMs.set(System.currentTimeMillis());
                log.info("[RATE_LIMIT] DEACTIVATED — Tomcat thread usage: {}", String.format("%.1f%%", usage * 100));
            }
        }
    }

    public boolean isActive() {
        return active.get();
    }

    double getTomcatThreadUsage() {
        try {
            double currentThreads = meterRegistry.get("tomcat.threads.current").gauge().value();
            double maxThreads = meterRegistry.get("tomcat.threads.config.max").gauge().value();
            if (maxThreads <= 0) return 0.0;
            return currentThreads / maxThreads;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
