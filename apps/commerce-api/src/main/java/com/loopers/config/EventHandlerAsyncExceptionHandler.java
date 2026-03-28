package com.loopers.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

@Slf4j
@RequiredArgsConstructor
public class EventHandlerAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private final MeterRegistry meterRegistry;

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("[ASYNC_ERROR] method={}, params={}", method.getName(), params, ex);
        meterRegistry.counter("event.handler.error", "method", method.getName()).increment();
    }
}
