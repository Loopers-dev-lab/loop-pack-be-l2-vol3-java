package com.loopers.domain.queue;

public interface SchedulerHeartbeatRepository {

    void recordHeartbeat(String schedulerName, int ttlSeconds);

    boolean isAlive(String schedulerName);
}
