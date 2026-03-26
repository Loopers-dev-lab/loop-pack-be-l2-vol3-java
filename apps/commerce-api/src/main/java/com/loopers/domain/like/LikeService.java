package com.loopers.domain.like;

import com.loopers.application.event.ApplicationDomainEventPublisher;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ApplicationDomainEventPublisher applicationDomainEventPublisher;

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = "productDetail", key = "'product:detail:' + #productId"),
        @CacheEvict(cacheNames = "productList", allEntries = true)
    })
    public LikeModel like(Long userId, Long productId) {
        ProductModel product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(like -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
            });

        LikeModel like = new LikeModel(userId, product);
        try {
            LikeModel savedLike = likeRepository.save(like);
            product.increaseLikeCount();
            applicationDomainEventPublisher.publishProductLikeChanged(productId, userId, 1);
            return savedLike;
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = "productDetail", key = "'product:detail:' + #productId"),
        @CacheEvict(cacheNames = "productList", allEntries = true)
    })
    public void unlike(Long userId, Long productId) {
        ProductModel product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        LikeModel like = likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요가 존재하지 않습니다."));

        likeRepository.delete(like);
        product.decreaseLikeCount();
        applicationDomainEventPublisher.publishProductLikeChanged(productId, userId, -1);
    }

    @Transactional(readOnly = true)
    public List<LikeModel> getMyLikes(Long userId) {
        return likeRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getLikeCount(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getLikeCountsByProductIds(List<Long> productIds) {
        return likeRepository.countByProductIds(productIds);
    }
}
