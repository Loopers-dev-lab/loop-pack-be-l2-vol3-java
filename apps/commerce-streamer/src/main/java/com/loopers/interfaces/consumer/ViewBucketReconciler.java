package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.BucketTimeUtils;
import com.loopers.domain.metrics.ProductViewMetric;
import com.loopers.domain.metrics.ProductViewMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewBucketReconciler {

    private static final Duration WATERMARK = Duration.ofMinutes(3);
    private static final String KEY_PREFIX = "metric:bucket:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductViewMetricRepository productViewMetricRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void reconcile() {
        Set<String> keys = scanKeys(KEY_PREFIX + "*");
        if (keys.isEmpty()) return;

        Instant cutoff = Instant.now().minus(WATERMARK);

        for (String key : keys) {
            try {
                Instant bucket = BucketTimeUtils.parseBucketEpochMillis(key);
                if (bucket.isAfter(cutoff)) continue;

                Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
                if (data.isEmpty()) continue;

                List<ProductViewMetric> metrics = data.entrySet().stream()
                        .map(entry -> new ProductViewMetric(
                                Long.parseLong((String) entry.getKey()),
                                BucketTimeUtils.toLocalDateTime(bucket),
                                Long.parseLong((String) entry.getValue())
                        ))
                        .toList();

                productViewMetricRepository.batchUpsert(metrics);
                redisTemplate.delete(key);

                log.info("View bucket reconcile 완료: bucket={}, products={}", bucket, metrics.size());
            } catch (Exception e) {
                log.error("View bucket reconcile 실패: key={}", key, e);
            }
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }
}
