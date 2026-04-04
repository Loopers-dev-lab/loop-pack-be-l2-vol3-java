package com.loopers.domain.queue;

public interface QueueModeRepository {

    QueueMode getCurrentMode();

    void updateMode(QueueMode mode);
}
