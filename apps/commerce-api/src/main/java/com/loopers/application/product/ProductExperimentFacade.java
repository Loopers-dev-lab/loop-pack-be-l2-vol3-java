package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.product.ProductCacheManager;
import com.loopers.infrastructure.product.ProductCacheManager.CachedPage;
import com.loopers.infrastructure.product.ProductLocalCacheManager;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductExperimentFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductCacheManager productCacheManager;
    private final ProductLocalCacheManager localCacheManager;

    // ===== Detail =====

    // v1: DB only
    @Transactional(readOnly = true)
    public ProductExperimentInfo getDetailV1(Long productId, Long userId) {
        Product product = productService.getActiveProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        ProductInfo info = ProductInfo.from(product, brand.getName());
        boolean liked = userId != null && likeService.isLiked(userId, productId);
        return ProductExperimentInfo.from(info, liked);
    }

    // v2: Redis → DB
    @Transactional(readOnly = true)
    public ProductExperimentInfo getDetailV2(Long productId, Long userId) {
        Optional<ProductInfo> cached = productCacheManager.getDetail(productId);
        ProductInfo info;
        if (cached.isPresent()) {
            info = cached.get();
        } else {
            Product product = productService.getActiveProduct(productId);
            Brand brand = brandService.getBrand(product.getBrandId());
            info = ProductInfo.from(product, brand.getName());
            productCacheManager.putDetail(productId, info);
        }
        boolean liked = userId != null && likeService.isLiked(userId, productId);
        return ProductExperimentInfo.from(info, liked);
    }

    // v3: Caffeine → Redis → DB
    @Transactional(readOnly = true)
    public ProductExperimentInfo getDetailV3(Long productId, Long userId) {
        Optional<ProductInfo> l1 = localCacheManager.getDetail(productId);
        if (l1.isPresent()) {
            boolean liked = userId != null && likeService.isLiked(userId, productId);
            return ProductExperimentInfo.from(l1.get(), liked);
        }

        Optional<ProductInfo> l2 = productCacheManager.getDetail(productId);
        ProductInfo info;
        if (l2.isPresent()) {
            info = l2.get();
        } else {
            Product product = productService.getActiveProduct(productId);
            Brand brand = brandService.getBrand(product.getBrandId());
            info = ProductInfo.from(product, brand.getName());
            productCacheManager.putDetail(productId, info);
        }

        localCacheManager.putDetail(productId, info);
        boolean liked = userId != null && likeService.isLiked(userId, productId);
        return ProductExperimentInfo.from(info, liked);
    }

    // ===== List Offset =====

    // v1: DB only (offset)
    @Transactional(readOnly = true)
    public Page<ProductInfo> getListOffsetV1(Long brandId, Pageable pageable) {
        Page<Product> products = productService.findActiveProducts(brandId, pageable);
        return toProductInfoPage(products);
    }

    // v2: Redis → DB (offset)
    @Transactional(readOnly = true)
    public Page<ProductInfo> getListOffsetV2(Long brandId, Pageable pageable) {
        String sort = pageable.getSort().toString();
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        Optional<CachedPage> cached = productCacheManager.getList(brandId, sort, page, size);
        if (cached.isPresent()) {
            CachedPage cp = cached.get();
            return new PageImpl<>(cp.content(), PageRequest.of(cp.page(), cp.size()), cp.totalElements());
        }

        Page<Product> products = productService.findActiveProducts(brandId, pageable);
        Page<ProductInfo> result = toProductInfoPage(products);
        productCacheManager.putList(brandId, sort, page, size,
                new CachedPage(result.getContent(), page, size, result.getTotalElements()));
        return result;
    }

    // v3: Caffeine → Redis → DB (offset)
    @Transactional(readOnly = true)
    public Page<ProductInfo> getListOffsetV3(Long brandId, Pageable pageable) {
        String sort = pageable.getSort().toString();
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        String l1Key = offsetCacheKey(brandId, sort, page, size);

        Optional<CachedPage> l1 = localCacheManager.getList(l1Key);
        if (l1.isPresent()) {
            CachedPage cp = l1.get();
            return new PageImpl<>(cp.content(), PageRequest.of(cp.page(), cp.size()), cp.totalElements());
        }

        Optional<CachedPage> l2 = productCacheManager.getList(brandId, sort, page, size);
        if (l2.isPresent()) {
            localCacheManager.putList(l1Key, l2.get());
            CachedPage cp = l2.get();
            return new PageImpl<>(cp.content(), PageRequest.of(cp.page(), cp.size()), cp.totalElements());
        }

        Page<Product> products = productService.findActiveProducts(brandId, pageable);
        Page<ProductInfo> result = toProductInfoPage(products);
        CachedPage cachedPage = new CachedPage(result.getContent(), page, size, result.getTotalElements());
        productCacheManager.putList(brandId, sort, page, size, cachedPage);
        localCacheManager.putList(l1Key, cachedPage);
        return result;
    }

    // ===== List Cursor =====

    // v1: DB only (cursor)
    @Transactional(readOnly = true)
    public CursorResult getListCursorV1(Long brandId, Long cursor, int size) {
        List<Product> products = productService.findActiveProductsCursor(brandId, cursor, size + 1);
        return toCursorResult(products, size);
    }

    // v2: Redis → DB (cursor)
    @Transactional(readOnly = true)
    public CursorResult getListCursorV2(Long brandId, Long cursor, int size) {
        String cacheKey = cursorCacheKey(brandId, cursor, size);
        Optional<CachedPage> cached = productCacheManager.getList(brandId, cacheKey, 0, size);
        if (cached.isPresent()) {
            CachedPage cp = cached.get();
            boolean hasNext = cp.totalElements() > 0;
            Long nextCursor = hasNext && !cp.content().isEmpty()
                    ? cp.content().get(cp.content().size() - 1).id()
                    : null;
            return new CursorResult(cp.content(), nextCursor, hasNext);
        }

        List<Product> products = productService.findActiveProductsCursor(brandId, cursor, size + 1);
        CursorResult result = toCursorResult(products, size);
        productCacheManager.putList(brandId, cacheKey, 0, size,
                new CachedPage(result.content(), 0, size, result.hasNext() ? 1 : 0));
        return result;
    }

    // v3: Caffeine → Redis → DB (cursor)
    @Transactional(readOnly = true)
    public CursorResult getListCursorV3(Long brandId, Long cursor, int size) {
        String cacheKey = cursorCacheKey(brandId, cursor, size);
        String l1Key = "cursor:" + cacheKey;

        Optional<CachedPage> l1 = localCacheManager.getList(l1Key);
        if (l1.isPresent()) {
            CachedPage cp = l1.get();
            boolean hasNext = cp.totalElements() > 0;
            Long nextCursor = hasNext && !cp.content().isEmpty()
                    ? cp.content().get(cp.content().size() - 1).id()
                    : null;
            return new CursorResult(cp.content(), nextCursor, hasNext);
        }

        Optional<CachedPage> l2 = productCacheManager.getList(brandId, cacheKey, 0, size);
        if (l2.isPresent()) {
            localCacheManager.putList(l1Key, l2.get());
            CachedPage cp = l2.get();
            boolean hasNext = cp.totalElements() > 0;
            Long nextCursor = hasNext && !cp.content().isEmpty()
                    ? cp.content().get(cp.content().size() - 1).id()
                    : null;
            return new CursorResult(cp.content(), nextCursor, hasNext);
        }

        List<Product> products = productService.findActiveProductsCursor(brandId, cursor, size + 1);
        CursorResult result = toCursorResult(products, size);
        CachedPage cachedPage = new CachedPage(result.content(), 0, size, result.hasNext() ? 1 : 0);
        productCacheManager.putList(brandId, cacheKey, 0, size, cachedPage);
        localCacheManager.putList(l1Key, cachedPage);
        return result;
    }

    // ===== Cache Stats =====

    public String getCacheStats() {
        return productCacheManager.getStats()
                + " | L1 Detail: " + localCacheManager.getDetailStats()
                + " | L1 List: " + localCacheManager.getListStats();
    }

    // ===== Private =====

    private Page<ProductInfo> toProductInfoPage(Page<Product> products) {
        Set<Long> brandIds = products.getContent().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        for (Product product : products.getContent()) {
            if (!brandMap.containsKey(product.getBrandId())) {
                throw new CoreException(ErrorType.NOT_FOUND,
                        "브랜드 매핑 누락. productId=" + product.getId() + ", brandId=" + product.getBrandId());
            }
        }

        return products.map(product -> ProductInfo.from(product, brandMap.get(product.getBrandId()).getName()));
    }

    private CursorResult toCursorResult(List<Product> products, int size) {
        boolean hasNext = products.size() > size;
        List<Product> content = hasNext ? products.subList(0, size) : products;

        Set<Long> brandIds = content.stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        List<ProductInfo> infoList = content.stream()
                .map(product -> ProductInfo.from(product, brandMap.get(product.getBrandId()).getName()))
                .toList();

        Long nextCursor = hasNext && !infoList.isEmpty()
                ? infoList.get(infoList.size() - 1).id()
                : null;

        return new CursorResult(infoList, nextCursor, hasNext);
    }

    private String offsetCacheKey(Long brandId, String sort, int page, int size) {
        String brandPart = brandId != null ? brandId.toString() : "all";
        return "offset:" + brandPart + ":" + sort + ":" + page + ":" + size;
    }

    private String cursorCacheKey(Long brandId, Long cursor, int size) {
        String brandPart = brandId != null ? brandId.toString() : "all";
        String cursorPart = cursor != null ? cursor.toString() : "start";
        return "cursor:" + brandPart + ":" + cursorPart + ":" + size;
    }

    public record CursorResult(List<ProductInfo> content, Long nextCursor, boolean hasNext) {
    }
}
