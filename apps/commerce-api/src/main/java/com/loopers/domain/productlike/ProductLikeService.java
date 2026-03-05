package com.loopers.domain.productlike;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;

    @Transactional
    public ProductLike registerLike(Long userId, Long productId) {
        // 중복 좋아요 확인
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }

        // 좋아요 등록
        ProductLike productLike = ProductLike.create(userId, productId);
        return productLikeRepository.save(productLike);
    }

    @Transactional
    public void cancelLike(Long userId, Long productId) {
        // 좋아요 존재 확인
        ProductLike productLike = productLikeRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요를 찾을 수 없습니다."));

        // 좋아요 삭제
        productLikeRepository.delete(productLike);
    }

    public Page<ProductLike> getLikesByUserId(Long userId, Pageable pageable) {
        return productLikeRepository.findAllByUserId(userId, pageable);
    }

    @Transactional
    public void deleteByProductId(Long productId) {
        productLikeRepository.deleteByProductId(productId);
    }

    @Transactional
    public void deleteByProductIds(List<Long> productIds) {
        productLikeRepository.deleteByProductIds(productIds);
    }
}
