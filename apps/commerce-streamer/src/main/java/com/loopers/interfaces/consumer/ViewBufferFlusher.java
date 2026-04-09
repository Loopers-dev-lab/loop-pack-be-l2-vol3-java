package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.ViewBuffer;
import com.loopers.application.metrics.ViewBuffer.BufferSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewBufferFlusher implements SmartLifecycle {

    private static final int MAX_BUFFER_SIZE = 100_000;
    private static final int MAX_RETRY = 3;

    private final ViewBuffer buffer;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaListenerEndpointRegistry registry;

    private volatile boolean running = false;

    @Scheduled(fixedDelay = 10_000)
    public void flush() {
        if (buffer.totalSize() > MAX_BUFFER_SIZE) {
            pauseConsumer();
            log.warn("View buffer 상한 초과 (size={}), consumer paused", buffer.totalSize());
        }

        Map<TopicPartition, BufferSnapshot> snapshot = buffer.drain();
        if (snapshot.isEmpty()) {
            resumeConsumer();
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                flushToRedis(snapshot);
                resumeConsumer();
                log.debug("View buffer flush 완료: partitions={}", snapshot.size());
                return;
            } catch (Exception e) {
                log.warn("View buffer flush 실패, attempt={}/{}", attempt + 1, MAX_RETRY, e);
                if (attempt < MAX_RETRY - 1) {
                    sleep(1000L * (1L << attempt));
                }
            }
        }

        buffer.restore(snapshot);
        log.error("View buffer flush {}회 실패, 다음 사이클 재시도", MAX_RETRY);
    }

    private void flushToRedis(Map<TopicPartition, BufferSnapshot> snapshot) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            snapshot.values().forEach(snap ->
                    snap.counts().forEach((key, count) -> {
                        String redisKey = "metric:bucket:" + key.bucket().toEpochMilli();
                        String field = String.valueOf(key.productId());
                        connection.hashCommands().hIncrBy(
                                redisKey.getBytes(),
                                field.getBytes(),
                                count
                        );
                        connection.keyCommands().expire(redisKey.getBytes(), 600);
                    })
            );
            return null;
        });
    }

    private void pauseConsumer() {
        try {
            MessageListenerContainer container = registry.getListenerContainer(ProductViewConsumer.CONSUMER_ID);
            if (container != null && container.isRunning()) {
                container.pause();
            }
        } catch (Exception e) {
            log.warn("Consumer pause 실패", e);
        }
    }

    private void resumeConsumer() {
        try {
            MessageListenerContainer container = registry.getListenerContainer(ProductViewConsumer.CONSUMER_ID);
            if (container != null && container.isContainerPaused()) {
                container.resume();
            }
        } catch (Exception e) {
            log.warn("Consumer resume 실패", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // SmartLifecycle — graceful shutdown

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        log.info("Graceful shutdown: final flush 시작");
        pauseConsumer();
        flush();
        log.info("Graceful shutdown: final flush 완료");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }
}
