package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 좋아요 도메인 서비스.
 * 상품 좋아요 등록(멱등)/취소(멱등), 사용자별 좋아요 목록 조회를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductService productService;

    /**
     * 상품에 좋아요를 등록한다. 이미 좋아요한 경우 무시한다 (멱등).
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @throws CoreException 상품이 존재하지 않을 때 (LIKE_PRODUCT_NOT_FOUND)
     */
    @Transactional
    public void addLike(String userId, String productId) {
        try {
            productService.findById(productId);
        } catch (CoreException e) {
            throw new CoreException(ErrorType.LIKE_PRODUCT_NOT_FOUND);
        }

        LikeId likeId = new LikeId(userId, productId);
        if (likeRepository.findById(likeId).isPresent()) {
            return;
        }

        LikeModel like = LikeModel.create(userId, productId);
        likeRepository.save(like);
    }

    /**
     * 상품 좋아요를 취소한다. 좋아요가 없으면 무시한다 (멱등).
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     */
    @Transactional
    public void removeLike(String userId, String productId) {
        LikeId likeId = new LikeId(userId, productId);
        likeRepository.findById(likeId).ifPresent(likeRepository::delete);
    }

    /**
     * 특정 사용자의 좋아요 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 좋아요 정보 DTO 목록
     */
    public List<LikeModel> getMyLikes(String userId) {
        return likeRepository.findAllByUserId(userId);
    }

    /**
     * 특정 상품의 좋아요 수를 조회한다.
     *
     * @param productId 상품 ID
     * @return 좋아요 수
     */
    public long countByProductId(String productId) {
        return likeRepository.countByProductId(productId);
    }

    /**
     * 여러 상품의 좋아요 수를 일괄 조회한다 (N+1 방지).
     *
     * @param productIds 상품 ID 목록
     * @return 상품 ID → 좋아요 수 맵
     */
    public Map<String, Long> countByProductIds(Collection<String> productIds) {
        return likeRepository.countByProductIds(productIds);
    }
}
