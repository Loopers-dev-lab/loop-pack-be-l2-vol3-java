package com.loopers.domain.like;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class LikeService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductService productService;

    public LikeService(ProductLikeRepository productLikeRepository, ProductService productService) {
        this.productLikeRepository = productLikeRepository;
        this.productService = productService;
    }

    @Transactional
    public int like(Long userId, Long productId) {
        Product product = productService.getDisplayableProduct(productId);
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(LikeErrorType.ALREADY_LIKED);
        }
        productLikeRepository.save(ProductLike.create(userId, productId));
        product.incrementLikeCount();
        return product.getLikeCount();
    }

    @Transactional
    public int unlike(Long userId, Long productId) {
        ProductLike productLike = productLikeRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new CoreException(LikeErrorType.LIKE_NOT_FOUND));
        productLikeRepository.delete(productLike);
        Product product = productService.getById(productId);
        product.decrementLikeCount();
        return product.getLikeCount();
    }

    @Transactional(readOnly = true)
    public List<ProductLike> getMyProductLikes(Long userId, int page, int size) {
        return productLikeRepository.findActiveByUserId(userId, page, size);
    }

    @Transactional(readOnly = true)
    public long countMyProductLikes(Long userId) {
        return productLikeRepository.countActiveByUserId(userId);
    }
}
