package com.loopers.application.like;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LikeFacade {
    private final LikeAppService likeAppService;
    private final ProductAppService productAppService;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public boolean toggleLike(Long userId, Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        Optional<Like> existingLike = likeRepository.findByUserIdAndProductId(userId, productId);
        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
            productRepository.decreaseLikeCount(productId);
            return false;
        }

        likeRepository.save(Like.create(userId, productId));
        productRepository.increaseLikeCount(productId);
        return true;
    }

    public List<LikeInfo> getLikedProducts(Long userId) {
        List<Like> likes = likeAppService.getLikesByUserId(userId);

        if (likes.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = likes.stream().map(Like::getProductId).toList();
        Map<Long, Product> productMap = productAppService.getByIds(productIds);

        return likes.stream()
                .map(like -> LikeInfo.of(like, productMap.get(like.getProductId())))
                .toList();
    }
}
