package com.loopers.application.product;

import com.loopers.application.brand.BrandAppService;
import com.loopers.application.like.LikeAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductFacade {
    private final ProductAppService productAppService;
    private final BrandAppService brandAppService;
    private final LikeAppService likeAppService;

    public Product createProduct(Long brandId, String name, Money price, int stock) {
        brandAppService.getById(brandId);
        return productAppService.create(brandId, name, price, stock);
    }

    public ProductInfo getProductDetail(Long productId, Long userId) {
        Product product = productAppService.getById(productId);
        Brand brand = brandAppService.getById(product.getBrandId());
        long likeCount = likeAppService.countByProductId(productId);
        boolean likedByUser = userId != null && likeAppService.isLikedByUser(userId, productId);

        return ProductInfo.of(product, brand, likeCount, likedByUser);
    }

    public List<ProductInfo> getProductList(ProductSortCondition condition, Long userId) {
        List<Product> products = productAppService.getProducts(condition);

        return products.stream()
                .map(product -> {
                    Brand brand = brandAppService.getById(product.getBrandId());
                    long likeCount = likeAppService.countByProductId(product.getId());
                    boolean likedByUser = userId != null && likeAppService.isLikedByUser(userId, product.getId());
                    return ProductInfo.of(product, brand, likeCount, likedByUser);
                })
                .toList();
    }

    public void likeProduct(Long userId, Long productId) {
        productAppService.getById(productId);
        likeAppService.addLike(userId, productId);
    }

    public void unlikeProduct(Long userId, Long productId) {
        likeAppService.removeLike(userId, productId);
    }
}
