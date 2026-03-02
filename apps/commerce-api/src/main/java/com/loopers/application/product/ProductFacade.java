package com.loopers.application.product;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final LikeService likeService;

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        long likeCount = likeService.getLikeCount(id);
        return ProductInfo.from(product, likeCount);
    }

    public Page<ProductInfo> getAll(Pageable pageable, ProductSortType sortType) {
        Pageable sortedPageable = applySorting(pageable, sortType);

        Page<ProductModel> products = productService.getAll(sortedPageable, sortType);

        List<Long> productIds = products.getContent().stream()
            .map(ProductModel::getId)
            .toList();

        Map<Long, Long> likeCounts = likeService.getLikeCountsByProductIds(productIds);

        return products.map(product -> ProductInfo.from(product, likeCounts.getOrDefault(product.getId(), 0L)));
    }

    public ProductInfo register(Long brandId, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        ProductModel product = productService.register(brandId, name, price, description, stockQuantity, status);
        return ProductInfo.from(product, 0L);
    }

    public ProductInfo update(Long id, Long brandId, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        ProductModel product = productService.update(id, brandId, name, price, description, stockQuantity, status);
        long likeCount = likeService.getLikeCount(id);
        return ProductInfo.from(product, likeCount);
    }

    public void delete(Long id) {
        productService.delete(id);
    }

    public List<ProductInfo> getMyLikedProducts(Long userId) {
        List<LikeModel> likes = likeService.getMyLikes(userId);

        List<Long> productIds = likes.stream()
            .map(like -> like.getProduct().getId())
            .toList();

        Map<Long, Long> likeCounts = likeService.getLikeCountsByProductIds(productIds);

        return likes.stream()
            .map(like -> ProductInfo.from(like.getProduct(), likeCounts.getOrDefault(like.getProduct().getId(), 0L)))
            .toList();
    }

    private Pageable applySorting(Pageable pageable, ProductSortType sortType) {
        return switch (sortType) {
            case LATEST -> PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
            case PRICE_ASC -> PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "price"));
            case LIKES_DESC -> PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        };
    }
}
