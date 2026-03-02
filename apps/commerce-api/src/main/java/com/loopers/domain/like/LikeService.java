package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 좋아요 도메인 서비스.
 * 추가/취소/목록 조회 외에, 상품별·목록별 좋아요 수 집계({@link #getLikeCount}, {@link #getLikeCountByProductIds})를
 * 제공하여 Application 레이어가 Repository를 직접 참조하지 않도록 캡슐화한다.
 */
@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductService productService;

    public LikeService(LikeRepository likeRepository, ProductService productService) {
        this.likeRepository = likeRepository;
        this.productService = productService;
    }

    /**
     * 좋아요를 추가한다. 상품 존재·미삭제 검증 후 1인 1좋아요 중복 시 CONFLICT.
     */
    @Transactional
    public LikeModel addLike(Long userId, Long productId) {
        if (userId == null || productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID와 상품 ID는 필수입니다.");
        }
        productService.findByIdAndNotDeleted(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        LikeModel like = LikeModel.create(userId, productId);
        return likeRepository.save(like);
    }

    /**
     * 좋아요를 취소한다.
     */
    @Transactional
    public void removeLike(Long userId, Long productId) {
        if (userId == null || productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID와 상품 ID는 필수입니다.");
        }
        LikeModel like = likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요를 찾을 수 없습니다."));
        likeRepository.delete(like);
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
}
