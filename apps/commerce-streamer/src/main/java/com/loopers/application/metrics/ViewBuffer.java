package com.loopers.application.metrics;

import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ViewBuffer {

    private final ConcurrentHashMap<TopicPartition, BufferState> partitionBuffers = new ConcurrentHashMap<>();

    public void increment(TopicPartition tp, Long productId, Instant bucket) {
        BufferState state = partitionBuffers.computeIfAbsent(tp, k -> new BufferState());
        BucketKey key = new BucketKey(productId, bucket);
        state.counts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    public Map<TopicPartition, BufferSnapshot> drain() {
        Map<TopicPartition, BufferSnapshot> result = new HashMap<>();
        partitionBuffers.forEach((tp, state) -> {
            BufferState old = partitionBuffers.put(tp, new BufferState());
            if (old != null && !old.counts.isEmpty()) {
                result.put(tp, old.toSnapshot());
            }
        });
        return result;
    }

    public void restore(Map<TopicPartition, BufferSnapshot> snapshot) {
        snapshot.forEach((tp, snap) -> {
            BufferState state = partitionBuffers.computeIfAbsent(tp, k -> new BufferState());
            snap.counts().forEach((key, count) ->
                    state.counts.computeIfAbsent(key, k -> new LongAdder()).add(count));
        });
    }

    public int totalSize() {
        return partitionBuffers.values().stream()
                .mapToInt(s -> s.counts.size()).sum();
    }

    static class BufferState {
        final ConcurrentHashMap<BucketKey, LongAdder> counts = new ConcurrentHashMap<>();

        BufferSnapshot toSnapshot() {
            Map<BucketKey, Long> snapshotCounts = new HashMap<>();
            counts.forEach((key, adder) -> snapshotCounts.put(key, adder.sum()));
            return new BufferSnapshot(snapshotCounts);
        }
    }

    public record BucketKey(Long productId, Instant bucket) {
    }

    public record BufferSnapshot(Map<BucketKey, Long> counts) {
    }
}
