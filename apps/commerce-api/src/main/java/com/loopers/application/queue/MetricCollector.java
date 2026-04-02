package com.loopers.application.queue;

public interface MetricCollector {

    String name();

    double collect();
}
