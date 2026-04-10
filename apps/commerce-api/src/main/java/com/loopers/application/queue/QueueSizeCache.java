package com.loopers.application.queue;

import org.springframework.stereotype.Component;

@Component
public class QueueSizeCache {

    private volatile long currentSize = 0;

    public void update(long size) {
        this.currentSize = size;
    }

    public long get() {
        return currentSize;
    }
}
