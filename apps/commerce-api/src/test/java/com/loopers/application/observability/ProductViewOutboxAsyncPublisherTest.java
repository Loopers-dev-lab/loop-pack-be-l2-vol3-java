package com.loopers.application.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        ProductViewOutboxAsyncPublisher.class,
        ProductViewOutboxAsyncPublisherTest.AsyncPublisherTestConfig.class
})
class ProductViewOutboxAsyncPublisherTest {

    @Configuration
    @EnableAsync
    static class AsyncPublisherTestConfig {

        @Bean
        ProductViewOutboxRecorder productViewOutboxRecorder() {
            return mock(ProductViewOutboxRecorder.class);
        }

        @Bean(name = "productViewOutboxExecutor")
        Executor productViewOutboxExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private ProductViewOutboxAsyncPublisher publisher;

    @Autowired
    private ProductViewOutboxRecorder recorder;

    @BeforeEach
    void resetRecorder() {
        reset(recorder);
    }

    @Test
    @DisplayName("recorder가 실패해도 schedule 호출부는 예외를 던지지 않는다.")
    void scheduleRecordProductViewed_whenRecorderThrows_shouldNotPropagate() {
        doThrow(new RuntimeException("outbox unavailable")).when(recorder).recordProductViewed(1L);

        assertThatCode(() -> publisher.scheduleRecordProductViewed(1L)).doesNotThrowAnyException();

        verify(recorder).recordProductViewed(1L);
    }

    @Test
    @DisplayName("productId가 null이면 recorder를 호출하지 않는다.")
    void scheduleRecordProductViewed_whenNull_shouldNoOp() {
        publisher.scheduleRecordProductViewed(null);

        verify(recorder, never()).recordProductViewed(org.mockito.ArgumentMatchers.any());
    }
}
