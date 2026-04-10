package com.loopers.domain.product;

import com.loopers.domain.product.event.ProductLikedEvent;
import com.loopers.domain.product.event.ProductUnlikedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductLikeService {
    private final ApplicationEventPublisher publisher;
    private final ProductLikeRepository productLikeRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        if (productLikeRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다");
        }
        try {
            ProductLikeModel liked = productLikeRepository.save(ProductLikeModel.create(userId, productId));
            publisher.publishEvent(ProductLikedEvent.from(liked));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다");
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        ProductLikeModel like = productLikeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 기록이 없습니다"));
        productLikeRepository.delete(like);
        publisher.publishEvent(ProductUnlikedEvent.from(like));
    }

    @Transactional(readOnly = true)
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return productLikeRepository.findByUserIdAndProductId(userId, productId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<ProductLikeModel> getLikesByUserId(Long userId) {
        return productLikeRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countLikes(Long productId) {
        return productLikeRepository.countByProductId(productId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countLikesByProductIds(List<Long> productIds) {
        return productLikeRepository.countByProductIds(productIds);
    }
}
