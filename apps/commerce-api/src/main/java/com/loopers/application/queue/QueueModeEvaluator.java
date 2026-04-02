package com.loopers.application.queue;

import com.loopers.config.DynamicQueueProperties;
import com.loopers.domain.queue.QueueMode;
import com.loopers.domain.queue.QueueModeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QueueModeEvaluator {

    private final DynamicQueueProperties dynamicProperties;
    private final QueueModeRepository queueModeRepository;
    private final Map<String, MetricCollector> collectorMap;
    private final AtomicLong lastModeChangeMs = new AtomicLong(0);

    public QueueModeEvaluator(
            DynamicQueueProperties dynamicProperties,
            QueueModeRepository queueModeRepository,
            List<MetricCollector> collectors
    ) {
        this.dynamicProperties = dynamicProperties;
        this.queueModeRepository = queueModeRepository;
        this.collectorMap = collectors.stream()
                .collect(Collectors.toMap(MetricCollector::name, Function.identity()));
    }

    @Scheduled(fixedDelayString = "${queue.dynamic.evaluation-interval-ms}")
    public void evaluate() {
        if (!dynamicProperties.enabled()) {
            return;
        }

        MetricCollector collector = collectorMap.get(dynamicProperties.metric());
        if (collector == null) {
            log.warn("[QUEUE_MODE] unknown metric: {}", dynamicProperties.metric());
            return;
        }

        double metricValue = collector.collect();
        QueueMode currentMode = queueModeRepository.getCurrentMode();

        QueueMode nextMode = determineNextMode(currentMode, metricValue);

        if (nextMode != currentMode) {
            queueModeRepository.updateMode(nextMode);
            lastModeChangeMs.set(System.currentTimeMillis());
            log.info("[QUEUE_MODE] {} → {} (metric={}, value={})",
                    currentMode, nextMode, dynamicProperties.metric(), metricValue);
        }
    }

    QueueMode determineNextMode(QueueMode currentMode, double metricValue) {
        if (currentMode == QueueMode.CLOSED) {
            return QueueMode.CLOSED;
        }

        if (currentMode == QueueMode.BYPASS && metricValue >= dynamicProperties.openThreshold()) {
            return QueueMode.OPEN;
        }

        if (currentMode == QueueMode.OPEN && metricValue < dynamicProperties.closeThreshold()) {
            if (isCooldownElapsed()) {
                return QueueMode.BYPASS;
            }
        }

        return currentMode;
    }

    boolean isCooldownElapsed() {
        long lastChange = lastModeChangeMs.get();
        if (lastChange == 0) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastChange;
        return elapsed >= dynamicProperties.cooldownSeconds() * 1000L;
    }
}
