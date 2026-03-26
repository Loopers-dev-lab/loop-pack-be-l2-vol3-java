package com.loopers.domain.like;

import com.loopers.domain.outbox.TransactionalOutboxWriter;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 좋아요 도메인 서비스.
 * 추가/취소 시 product_stats.like_count 아토믹 업데이트로 동기화한다.
 */
@Service
public class LikeService {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String PRODUCT_LIKE_CHANGED = "PRODUCT_LIKE_CHANGED";

    private final LikeRepository likeRepository;
    private final ProductService productService;
    private final ProductStatsRepository productStatsRepository;
    private final TransactionalOutboxWriter transactionalOutboxWriter;
    private final LikeService self;

    public LikeService(LikeRepository likeRepository, ProductService productService,
            ProductStatsRepository productStatsRepository,
            TransactionalOutboxWriter transactionalOutboxWriter,
            @Lazy LikeService self) {
        this.likeRepository = likeRepository;
        this.productService = productService;
        this.productStatsRepository = productStatsRepository;
        this.transactionalOutboxWriter = Objects.requireNonNull(transactionalOutboxWriter);
        this.self = self;
    }

    private static final int ADD_LIKE_MAX_RETRIES = 10;
    private static final long RETRY_BACKOFF_INITIAL_MS = 30L;

    /**
     * 좋아요를 추가한다. 상품 존재·미삭제 검증 후 1인 1좋아요 중복 시 CONFLICT.
     * 락 순서 통일: likes INSERT 먼저 → product_stats 갱신. (데드락 방지, like-concurrency-deadlock-alternatives.md §A)
     * 데드락/락 타임아웃 시 지수 백오프로 최대 ADD_LIKE_MAX_RETRIES회 재시도. (§B) 원자성은 doAddLike 단일 트랜잭션으로 보장.
     * addLike는 트랜잭션 없이 검증만 하고, 실제 쓰기는 doAddLike(REQUIRES_NEW)에서 수행해 동시 호출 시 커넥션 풀 고갈을 막는다.
     */
    public LikeModel addLike(Long userId, Long productId) {
        if (userId == null || productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID와 상품 ID는 필수입니다.");
        }
        productService.findByIdAndNotDeleted(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        self.ensureProductStatsExists(productId);
        int attempts = 0;
        while (true) {
            try {
                return self.doAddLike(userId, productId);
            } catch (RuntimeException e) {
                DataAccessException dae = findDataAccessException(e);
                if (dae == null || !isDeadlockOrLockTimeout(dae)) {
                    throw e;
                }
                if (++attempts >= ADD_LIKE_MAX_RETRIES) {
                    throw new CoreException(ErrorType.INTERNAL_ERROR,
                            "좋아요 처리 재시도 한도 초과 (데드락/락타임아웃): " + e.getMessage(), e);
                }
                long backoffMs = RETRY_BACKOFF_INITIAL_MS * (1L << (attempts - 1));
                backoffMs += ThreadLocalRandom.current().nextLong(0, 25L);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CoreException(ErrorType.INTERNAL_ERROR, "좋아요 처리 중 중단됨");
                }
            }
        }
    }

    private static DataAccessException findDataAccessException(Throwable t) {
        while (t != null) {
            if (t instanceof DataAccessException) {
                return (DataAccessException) t;
            }
            t = t.getCause();
        }
        return null;
    }

    /** product_stats 행이 없으면 생성. doAddLike와 분리해 같은 트랜잭션 내 INSERT IGNORE+UPDATE 데드락 제거. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureProductStatsExists(Long productId) {
        productStatsRepository.createIfAbsent(productId);
    }

    /** 단일 트랜잭션: likes INSERT → product_stats UPDATE만. (createIfAbsent는 ensureProductStatsExists에서 선행) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LikeModel doAddLike(Long userId, Long productId) {
        LikeModel like = LikeModel.create(userId, productId);
        LikeModel saved = likeRepository.save(like);
        productStatsRepository.incrementLikeCount(productId);
        transactionalOutboxWriter.record(
                CATALOG_EVENTS_TOPIC,
                String.valueOf(productId),
                PRODUCT_LIKE_CHANGED,
                Map.of(
                        "productId", productId,
                        "userId", userId,
                        "action", "LIKED"));
        return saved;
    }

    private static boolean isDeadlockOrLockTimeout(DataAccessException e) {
        String msg = e.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("deadlock") || lower.contains("1213") || lower.contains("1205") || lower.contains("40001") || lower.contains("lock")) {
                return true;
            }
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            String cmsg = cause.getMessage();
            if (cmsg != null) {
                String lower = cmsg.toLowerCase();
                if (lower.contains("deadlock") || lower.contains("1213") || lower.contains("1205") || lower.contains("40001") || lower.contains("lock")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 좋아요를 취소한다. 취소 후 product_stats.like_count를 아토믹 감소시킨다.
     */
    @Transactional
    public void removeLike(Long userId, Long productId) {
        if (userId == null || productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID와 상품 ID는 필수입니다.");
        }
        LikeModel like = likeRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요를 찾을 수 없습니다."));
        likeRepository.delete(like);
        productStatsRepository.decrementLikeCount(productId);
        transactionalOutboxWriter.record(
                CATALOG_EVENTS_TOPIC,
                String.valueOf(productId),
                PRODUCT_LIKE_CHANGED,
                Map.of(
                        "productId", productId,
                        "userId", userId,
                        "action", "UNLIKED"));
    }

    /**
     * 사용자별 좋아요 목록을 페이지로 조회한다.
     */
    @Transactional(readOnly = true)
    public Page<LikeModel> findLikesByUserId(Long userId, Pageable pageable) {
        return likeRepository.findByUserId(userId, pageable);
    }

    /**
     * 상품별 좋아요 수를 반환한다. (상품 상세 등 집계용)
     */
    @Transactional(readOnly = true)
    public long getLikeCount(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    /**
     * 상품 ID 목록별 좋아요 수를 반환한다. 목록에 없는 상품은 0으로 간주한다.
     * null/empty 입력 시 빈 Map을 반환해 불필요한 Repository 호출을 막는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getLikeCountByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return likeRepository.countByProductIds(productIds);
    }

    /**
     * product_stats 기반 상품별 좋아요 수. (PDP/PLP 읽기 경로, likes 테이블 미조회)
     */
    @Transactional(readOnly = true)
    public long getLikeCountFromStats(Long productId) {
        return productStatsRepository.findByProductId(productId)
                .map(ProductStatsModel::getLikeCount)
                .orElse(0L);
    }

    /**
     * product_stats 기반 상품 ID 목록별 좋아요 수. (PLP 읽기 경로)
     * 없는 상품은 map에 없으므로 호출부에서 getOrDefault(id, 0L) 사용.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getLikeCountByProductIdsFromStats(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productStatsRepository.findLikeCountByProductIds(productIds);
    }
}
