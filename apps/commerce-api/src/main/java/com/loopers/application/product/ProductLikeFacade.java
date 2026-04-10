package com.loopers.application.product;

import com.loopers.application.product.dto.ProductLikeResult;
import com.loopers.domain.product.ProductLikeService;
import com.loopers.domain.product.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductLikeFacade {
    private final ProductLikeService productLikeService;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.validateExists(productId);
        productLikeService.like(userId, productId);
        productService.incrementLikeCount(productId);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        productLikeService.unlike(userId, productId);
        productService.decrementLikeCount(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductLikeResult> getMyLikedProducts(Long userId) {
        return productLikeService.getLikesByUserId(userId).stream()
            .filter(like -> productService.existsById(like.getProductId()))
            .map(ProductLikeResult::from)
            .toList();
    }
}
