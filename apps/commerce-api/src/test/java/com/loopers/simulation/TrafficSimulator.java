package com.loopers.simulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class TrafficSimulator {

    public record Config(
            int totalUsers,
            int serverCapacity,
            int processingTicks,
            int msPerTick,
            int rateLimitRetryTicks,
            int queuePollMinTicks,
            int queuePollMaxTicks,
            int batchSize
    ) {
        public static Config defaults() {
            return new Config(200, 80, 3, 100, 1, 5, 30, 80);
        }
    }

    private sealed interface UserState
            permits UserState.Pending, UserState.QueueWaiting, UserState.Active, UserState.Done {
        record Pending(int nextAttemptTick) implements UserState {}
        record QueueWaiting(int nextPollTick) implements UserState {}
        record Active(int completionTick) implements UserState {}
        record Done() implements UserState {}
    }

    private final Config config;

    public TrafficSimulator(Config config) {
        this.config = config;
    }

    public SimulationResult runRateLimiting() {
        var states = initPending();
        int tick = 0;
        int totalRequests = 0;
        int retryCount = 0;
        int maxConcurrent = 0;
        int maxTicks = config.totalUsers() * config.processingTicks();

        while (hasIncomplete(states) && tick < maxTicks) {
            // 1. 완료 처리
            for (int i = 0; i < states.size(); i++) {
                if (states.get(i) instanceof UserState.Active a && a.completionTick() == tick) {
                    states.set(i, new UserState.Done());
                }
            }

            // 2. 슬롯 계산
            int activeCount = countActive(states);
            maxConcurrent = Math.max(maxConcurrent, activeCount);
            int slots = config.serverCapacity() - activeCount;

            // 3. 재시도 가능한 Pending 사용자 처리
            for (int i = 0; i < states.size(); i++) {
                if (states.get(i) instanceof UserState.Pending p && p.nextAttemptTick() <= tick) {
                    totalRequests++;
                    if (slots > 0) {
                        states.set(i, new UserState.Active(tick + config.processingTicks()));
                        slots--;
                    } else {
                        states.set(i, new UserState.Pending(tick + config.rateLimitRetryTicks()));
                        retryCount++;
                    }
                }
            }

            tick++;
        }

        int success = (int) states.stream().filter(s -> s instanceof UserState.Done).count();
        return SimulationResult.of("Rate Limiting", totalRequests, success, maxConcurrent, retryCount, tick, config.msPerTick());
    }

    public SimulationResult runQueue() {
        var states = initPending();
        var waitingQueue = new ArrayDeque<Integer>();
        int tick = 0;
        int totalRequests = 0;
        int retryCount = 0;
        int maxConcurrent = 0;
        int nextBatchTick = config.processingTicks();
        int maxTicks = config.totalUsers() * config.processingTicks() * 5;

        // tick=0: 전원 대기열 등록
        int pollRange = config.queuePollMaxTicks() - config.queuePollMinTicks();
        for (int i = 0; i < states.size(); i++) {
            totalRequests++;
            int pollInterval = config.queuePollMinTicks() + (i % (pollRange + 1));
            states.set(i, new UserState.QueueWaiting(tick + pollInterval));
            waitingQueue.add(i);
        }

        while (hasIncomplete(states) && tick < maxTicks) {
            // 1. 완료 처리
            for (int i = 0; i < states.size(); i++) {
                if (states.get(i) instanceof UserState.Active a && a.completionTick() == tick) {
                    states.set(i, new UserState.Done());
                }
            }

            // 2. 배치 스케줄러 실행
            int activeCount = countActive(states);
            maxConcurrent = Math.max(maxConcurrent, activeCount);

            if (tick == nextBatchTick) {
                int slots = config.serverCapacity() - activeCount;
                int promoted = 0;
                while (!waitingQueue.isEmpty() && promoted < Math.min(config.batchSize(), slots)) {
                    int userId = waitingQueue.peek();
                    if (states.get(userId) instanceof UserState.QueueWaiting) {
                        waitingQueue.poll();
                        states.set(userId, new UserState.Active(tick + config.processingTicks()));
                        promoted++;
                    } else {
                        waitingQueue.poll();
                    }
                }
                nextBatchTick = tick + config.processingTicks();
            }

            // 3. 폴링 처리
            for (int i = 0; i < states.size(); i++) {
                if (states.get(i) instanceof UserState.QueueWaiting qw && qw.nextPollTick() == tick) {
                    totalRequests++;
                    retryCount++;
                    int pollInterval = config.queuePollMinTicks() + (i % (pollRange + 1));
                    states.set(i, new UserState.QueueWaiting(tick + pollInterval));
                }
            }

            tick++;
        }

        int success = (int) states.stream().filter(s -> s instanceof UserState.Done).count();
        return SimulationResult.of("Queue", totalRequests, success, maxConcurrent, retryCount, tick, config.msPerTick());
    }

    private List<UserState> initPending() {
        var states = new ArrayList<UserState>(config.totalUsers());
        for (int i = 0; i < config.totalUsers(); i++) {
            states.add(new UserState.Pending(0));
        }
        return states;
    }

    private boolean hasIncomplete(List<UserState> states) {
        return states.stream().anyMatch(s -> !(s instanceof UserState.Done));
    }

    private int countActive(List<UserState> states) {
        return (int) states.stream().filter(s -> s instanceof UserState.Active).count();
    }
}
