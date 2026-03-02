package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}
