package com.loopers.application.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QueueThroughputPolicy {

    private final int maxBatchSize;
    private final long schedulerFixedDelayMs;
    private final int dbPoolMaxSize;
    private final double dbConnectionUtilization;
    private final long avgOrderProcessingMs;

    public QueueThroughputPolicy(
        @Value("${queue.scheduler.max-batch-size:50}") int maxBatchSize,
        @Value("${queue.scheduler.fixed-delay-ms:1000}") long schedulerFixedDelayMs,
        @Value("${datasource.mysql-jpa.main.maximum-pool-size:40}") int dbPoolMaxSize,
        @Value("${queue.throughput.db-connection-utilization:0.7}") double dbConnectionUtilization,
        @Value("${queue.throughput.avg-order-processing-ms:250}") long avgOrderProcessingMs
    ) {
        this.maxBatchSize = maxBatchSize;
        this.schedulerFixedDelayMs = schedulerFixedDelayMs;
        this.dbPoolMaxSize = dbPoolMaxSize;
        this.dbConnectionUtilization = dbConnectionUtilization;
        this.avgOrderProcessingMs = avgOrderProcessingMs;
    }

    public int calculateBatchSize() {
        double connectionsForOrder = dbPoolMaxSize * dbConnectionUtilization;
        double intervalCapacity = connectionsForOrder * ((double) schedulerFixedDelayMs / avgOrderProcessingMs);
        int dynamicBatch = (int) Math.floor(intervalCapacity);
        int boundedBatch = Math.min(maxBatchSize, Math.max(1, dynamicBatch));
        return boundedBatch;
    }

    public long estimateWaitSeconds(long position) {
        long ahead = Math.max(0, position - 1);
        double admittedPerSecond = calculateBatchSize() * (1000.0 / schedulerFixedDelayMs);
        if (admittedPerSecond <= 0) {
            return 0;
        }
        return (long) Math.ceil(ahead / admittedPerSecond);
    }

    public int recommendPollingSeconds(Long position) {
        if (position == null || position <= 0) {
            return 15;
        }
        if (position <= 20) {
            return 1;
        }
        if (position <= 100) {
            return 2;
        }
        if (position <= 1000) {
            return 5;
        }
        return 10;
    }
}
