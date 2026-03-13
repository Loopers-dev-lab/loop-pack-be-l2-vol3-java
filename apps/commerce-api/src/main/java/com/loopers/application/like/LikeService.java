package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    // 좋아요가 바뀌면 캐시도 같이 무효화해야 정합성이 맞아서 evict 추가
    @Caching(evict = {
        @CacheEvict(value = "product:detail", key = "#productId"),
        @CacheEvict(value = "product:list", allEntries = true)
    })
    @Transactional
    public void like(Long memberId, Long productId) {
        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        var product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        likeRepository.save(new Like(memberId, productId));
        // JPA dirty checking으로 별도 save 없이 UPDATE됨
        product.increaseLikeCount();
    }

    @Caching(evict = {
        @CacheEvict(value = "product:detail", key = "#productId"),
        @CacheEvict(value = "product:list", allEntries = true)
    })
    @Transactional
    public void unlike(Long memberId, Long productId) {
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        var product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        likeRepository.deleteByMemberIdAndProductId(memberId, productId);
        product.decreaseLikeCount();
    }
}
