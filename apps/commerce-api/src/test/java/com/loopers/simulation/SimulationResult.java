package com.loopers.simulation;

public record SimulationResult(
        String strategyName,
        int totalRequestsGenerated,
        int successCount,
        int maxConcurrentRequests,
        int retryCount,
        int totalDurationTicks,
        int totalDurationMs
) {
    public static SimulationResult of(
            String strategyName,
            int totalRequests,
            int success,
            int maxConcurrent,
            int retryCount,
            int durationTicks,
            int msPerTick
    ) {
        return new SimulationResult(
                strategyName, totalRequests, success,
                maxConcurrent, retryCount, durationTicks, durationTicks * msPerTick
        );
    }
}
