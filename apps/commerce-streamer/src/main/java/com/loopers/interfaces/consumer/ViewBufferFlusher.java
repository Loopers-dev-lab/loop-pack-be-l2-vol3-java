package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.ViewBuffer;
import com.loopers.application.metrics.ViewBuffer.BucketKey;
import com.loopers.application.metrics.ViewBuffer.BufferSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewBufferFlusher implements SmartLifecycle {

    private static final int MAX_BUFFER_SIZE = 100_000;
    private static final int MAX_RETRY = 3;
    private static final int TTL_SECONDS = 600;

    private final ViewBuffer buffer;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaListenerEndpointRegistry registry;
    private final DefaultRedisScript<Long> viewBucketFlushScript;

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

        Map<TopicPartition, BufferSnapshot> currentTarget = snapshot;
        Set<Long> lastFailedBuckets = null;

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                flushToRedis(currentTarget);
                resumeConsumer();
                log.debug("View buffer flush 완료: partitions={}", snapshot.size());
                return;
            } catch (PartialFlushException e) {
                lastFailedBuckets = e.failedBuckets;
                currentTarget = filterByBuckets(snapshot, lastFailedBuckets);
                log.warn("View buffer flush 부분 실패, attempt={}/{}, failedBuckets={}",
                        attempt + 1, MAX_RETRY, lastFailedBuckets.size());
                if (attempt < MAX_RETRY - 1) {
                    sleep(1000L * (1L << attempt));
                }
            } catch (Exception e) {
                lastFailedBuckets = extractAllBuckets(currentTarget);
                log.warn("View buffer flush 실패, attempt={}/{}", attempt + 1, MAX_RETRY, e);
                if (attempt < MAX_RETRY - 1) {
                    sleep(1000L * (1L << attempt));
                }
            }
        }

        if (lastFailedBuckets != null && !lastFailedBuckets.isEmpty()) {
            Map<TopicPartition, BufferSnapshot> failedSnapshot = filterByBuckets(snapshot, lastFailedBuckets);
            buffer.restore(failedSnapshot);
            log.error("View buffer flush {}회 실패, 실패 bucket {} 개 restore", MAX_RETRY, lastFailedBuckets.size());
        }
    }

    private void flushToRedis(Map<TopicPartition, BufferSnapshot> snapshot) {
        Map<Long, Map<Long, Long>> byBucket = groupByBucket(snapshot);

        Set<Long> failedBuckets = new HashSet<>();
        for (var entry : byBucket.entrySet()) {
            Long bucketMillis = entry.getKey();
            Map<Long, Long> productCounts = entry.getValue();

            String redisKey = "metric:bucket:" + bucketMillis;
            List<String> keys = List.of(redisKey);

            List<String> args = new ArrayList<>();
            args.add(String.valueOf(TTL_SECONDS));
            productCounts.forEach((pid, count) -> {
                args.add(String.valueOf(pid));
                args.add(String.valueOf(count));
            });

            try {
                redisTemplate.execute(viewBucketFlushScript, keys, args.toArray(new String[0]));
            } catch (Exception e) {
                log.warn("bucket flush 실패: bucket={}, error={}", bucketMillis, e.getMessage());
                failedBuckets.add(bucketMillis);
            }
        }

        if (!failedBuckets.isEmpty()) {
            throw new PartialFlushException(failedBuckets);
        }
    }

    private Map<Long, Map<Long, Long>> groupByBucket(Map<TopicPartition, BufferSnapshot> snapshot) {
        Map<Long, Map<Long, Long>> byBucket = new HashMap<>();
        snapshot.values().forEach(snap ->
                snap.counts().forEach((key, count) ->
                        byBucket
                                .computeIfAbsent(key.bucket().toEpochMilli(), k -> new HashMap<>())
                                .merge(key.productId(), count, Long::sum)
                )
        );
        return byBucket;
    }

    private Map<TopicPartition, BufferSnapshot> filterByBuckets(
            Map<TopicPartition, BufferSnapshot> snapshot, Set<Long> targetBuckets
    ) {
        Map<TopicPartition, BufferSnapshot> filtered = new HashMap<>();
        snapshot.forEach((tp, snap) -> {
            Map<BucketKey, Long> filteredCounts = new HashMap<>();
            snap.counts().forEach((key, count) -> {
                if (targetBuckets.contains(key.bucket().toEpochMilli())) {
                    filteredCounts.put(key, count);
                }
            });
            if (!filteredCounts.isEmpty()) {
                filtered.put(tp, new BufferSnapshot(filteredCounts));
            }
        });
        return filtered;
    }

    private Set<Long> extractAllBuckets(Map<TopicPartition, BufferSnapshot> snapshot) {
        Set<Long> buckets = new HashSet<>();
        snapshot.values().forEach(snap ->
                snap.counts().keySet().forEach(key -> buckets.add(key.bucket().toEpochMilli()))
        );
        return buckets;
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

    private static class PartialFlushException extends RuntimeException {
        final Set<Long> failedBuckets;

        PartialFlushException(Set<Long> failedBuckets) {
            super("bucket flush 부분 실패: " + failedBuckets.size() + "개");
            this.failedBuckets = failedBuckets;
        }
    }
}
