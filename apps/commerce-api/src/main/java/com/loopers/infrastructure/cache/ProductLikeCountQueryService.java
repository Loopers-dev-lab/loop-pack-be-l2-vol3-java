package com.loopers.infrastructure.cache;

import com.loopers.domain.like.LikeRepository;
import com.loopers.infrastructure.cache.LikeCountCacheService.CacheResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 상품 좋아요 수 조회 서비스.
 *
 * <h3>캐시 스탬피드 방지 전략</h3>
 * <ol>
 *   <li><b>PER (Probabilistic Early Recomputation)</b>: TTL 만료 전에 확률적으로 갱신</li>
 *   <li><b>Single Flight</b>: 동시 캐시 미스 시 하나의 요청만 DB 조회</li>
 *   <li><b>Stale-While-Revalidate</b>: 기존 값 반환하면서 백그라운드에서 갱신</li>
 * </ol>
 */
@Slf4j
@Service
public class ProductLikeCountQueryService {

    private static final String LOCK_KEY_PREFIX = "product:like-count:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration RETRY_DELAY = Duration.ofMillis(50);
    private static final int MAX_RETRIES = 3;

    private final LikeCountCacheService likeCountCacheService;
    private final DistributedLockService distributedLockService;
    private final LikeRepository likeRepository;

    public ProductLikeCountQueryService(
            LikeCountCacheService likeCountCacheService,
            DistributedLockService distributedLockService,
            LikeRepository likeRepository
    ) {
        this.likeCountCacheService = likeCountCacheService;
        this.distributedLockService = distributedLockService;
        this.likeRepository = likeRepository;
    }

    /**
     * 상품 좋아요 수 조회.
     *
     * <pre>
     * 1. 캐시 조회 (with PER 신호)
     * 2. 캐시 히트 + 갱신 불필요 → 즉시 반환
     * 3. 캐시 히트 + 갱신 필요 → Stale 값 반환 + 백그라운드 갱신
     * 4. 캐시 미스 → Single Flight로 DB 조회
     * </pre>
     *
     * @param productId 상품 ID
     * @return 좋아요 수
     */
    public long getLikeCount(Long productId) {
        // 1. 캐시 조회 (with PER)
        CacheResult result = likeCountCacheService.getWithRefreshSignal(productId);

        if (result.hit()) {
            if (result.shouldRefresh()) {
                // Stale-While-Revalidate: 기존 값 반환 + 백그라운드 갱신
                refreshInBackground(productId);
            }
            return result.value();
        }

        // 2. 캐시 미스 → Single Flight
        return loadWithSingleFlight(productId);
    }

    /**
     * 여러 상품의 좋아요 수 일괄 조회.
     */
    public Map<Long, Long> getLikeCounts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        // 1. 캐시에서 일괄 조회
        Map<Long, Long> cached = likeCountCacheService.getMultiple(productIds);
        Map<Long, Long> result = new HashMap<>(cached);

        // 2. 캐시 미스된 ID 추출
        List<Long> missingIds = productIds.stream()
                .filter(id -> !cached.containsKey(id))
                .toList();

        if (missingIds.isEmpty()) {
            return result;
        }

        // 3. 캐시 미스 항목 DB 조회 및 캐시 저장
        Map<Long, Long> fromDb = likeRepository.countByProductIds(missingIds);
        for (Long id : missingIds) {
            long count = fromDb.getOrDefault(id, 0L);
            result.put(id, count);
            likeCountCacheService.set(id, count);
        }

        return result;
    }

    /**
     * Single Flight 패턴으로 DB 조회.
     * 하나의 요청만 DB 조회, 나머지는 대기 후 캐시에서 조회.
     */
    private long loadWithSingleFlight(Long productId) {
        String lockKey = LOCK_KEY_PREFIX + productId;

        Optional<String> lockValue = distributedLockService.tryLock(lockKey, LOCK_TTL);

        if (lockValue.isPresent()) {
            try {
                // Double-check: 다른 스레드가 이미 캐시에 저장했을 수 있음
                Optional<Long> cachedAgain = likeCountCacheService.get(productId);
                if (cachedAgain.isPresent()) {
                    return cachedAgain.get();
                }

                long count = likeRepository.countByProductId(productId);
                likeCountCacheService.set(productId, count);
                log.debug("DB에서 좋아요 수 조회 및 캐시 저장: productId={}, count={}", productId, count);
                return count;
            } finally {
                distributedLockService.unlock(lockKey, lockValue.get());
            }
        }

        // 락 획득 실패 → 대기 후 캐시 재조회
        return waitAndRetryFromCache(productId);
    }

    /**
     * 락 획득 실패 시 대기 후 캐시 재조회.
     * 최대 재시도 횟수 초과 시 DB 직접 조회 (Fallback).
     */
    private long waitAndRetryFromCache(Long productId) {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            try {
                Thread.sleep(RETRY_DELAY.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Optional<Long> cached = likeCountCacheService.get(productId);
            if (cached.isPresent()) {
                log.debug("재시도 후 캐시에서 조회 성공: productId={}, retry={}", productId, retry);
                return cached.get();
            }
        }

        // Fallback: 재시도 모두 실패 시 DB 직접 조회
        log.warn("캐시 재시도 실패, DB 직접 조회: productId={}", productId);
        return likeRepository.countByProductId(productId);
    }

    /**
     * 백그라운드에서 캐시 갱신 (Stale-While-Revalidate).
     * PER 신호를 받은 요청이 비동기로 캐시를 갱신.
     */
    @Async
    public void refreshInBackground(Long productId) {
        String lockKey = LOCK_KEY_PREFIX + "refresh:" + productId;

        // 갱신 중복 방지를 위한 락
        Optional<String> lockValue = distributedLockService.tryLock(lockKey, Duration.ofSeconds(10));
        if (lockValue.isEmpty()) {
            // 이미 다른 요청이 갱신 중
            return;
        }

        try {
            long count = likeRepository.countByProductId(productId);
            likeCountCacheService.set(productId, count);
            log.debug("백그라운드 캐시 갱신 완료: productId={}, count={}", productId, count);
        } catch (Exception e) {
            log.warn("백그라운드 캐시 갱신 실패: productId={}", productId, e);
        } finally {
            distributedLockService.unlock(lockKey, lockValue.get());
        }
    }
}
