package com.loopers.infrastructure.waitingroom.metrics;

public final class WaitingRoomMetricNames {

    // Counter (누적)
    public static final String ENTER_TOTAL = "queue.enter.total";
    public static final String ADMIT_TOTAL = "queue.admit.total";
    public static final String CANCEL_TOTAL = "queue.cancel.total";
    public static final String SCHEDULER_RUN_TOTAL = "queue.scheduler.run.total";
    public static final String TOKEN_EXPIRED_TOTAL = "queue.token.expired.total";
    public static final String TOKEN_COMPLETED_TOTAL = "queue.token.completed.total";

    // Gauge (현재 상태)
    public static final String WAITING_SIZE = "queue.waiting.size";
    public static final String ENTRY_GATE_ACTIVE = "queue.entry_gate.active";

    // Distribution Summary — 신규
    public static final String ADMIT_BATCH_SIZE = "queue.admit.batch.size";

    // Timer (실행 시간)
    public static final String SCHEDULER_DURATION = "queue.scheduler.duration";
    public static final String WAIT_DURATION = "queue.wait.duration.seconds";

    private WaitingRoomMetricNames() {
    }
}
