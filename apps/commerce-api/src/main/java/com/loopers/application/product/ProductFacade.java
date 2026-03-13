package com.loopers.application.product;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    public static final String PRODUCT_DETAIL_CACHE = "productDetail";
    public static final String PRODUCT_LIST_CACHE = "productList";

    private final ProductService productService;
    private final LikeService likeService;

    @Cacheable(cacheNames = PRODUCT_DETAIL_CACHE, key = "'product:detail:' + #id")
    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    @Cacheable(
        cacheNames = PRODUCT_LIST_CACHE,
        key = "'product:list:brand:' + (#brandId == null ? 'all' : #brandId) + ':sort:' + #sortType.name() + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize"
    )
    public ProductPageInfo getAll(Pageable pageable, ProductSortType sortType, Long brandId) {
        Pageable sortedPageable = applySorting(pageable, sortType);

        Page<ProductModel> products = productService.getAll(sortedPageable, sortType, brandId);
        return ProductPageInfo.from(products.map(ProductInfo::from));
    }

    @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)
    public ProductInfo register(Long brandId, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        ProductModel product = productService.register(brandId, name, price, description, stockQuantity, status);
        return ProductInfo.from(product);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = PRODUCT_DETAIL_CACHE, key = "'product:detail:' + #id"),
        @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)
    })
    public ProductInfo update(Long id, Long brandId, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        ProductModel product = productService.update(id, brandId, name, price, description, stockQuantity, status);
        return ProductInfo.from(product);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = PRODUCT_DETAIL_CACHE, key = "'product:detail:' + #id"),
        @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void delete(Long id) {
        productService.delete(id);
    }

    public List<ProductInfo> getMyLikedProducts(Long userId) {
        List<LikeModel> likes = likeService.getMyLikes(userId);

        return likes.stream()
            .map(like -> ProductInfo.from(like.getProduct()))
            .toList();
    }

    private Pageable applySorting(Pageable pageable, ProductSortType sortType) {
        return switch (sortType) {
            case LATEST -> PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
            case PRICE_ASC -> PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.ASC, "id")));
            case LIKES_DESC -> PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        };
    }
}
