package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.cache.ProductCacheManager;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.common.CursorResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.common.event.ProductViewedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 고객 상품 조회 Facade
 *
 * Product + Brand 도메인 서비스를 조합하여 고객 상품 조회 유스케이스를 처리한다.
 *
 * 캐시 전략:
 *   - @Transactional 미사용: 캐시 히트 시 DB 커넥션을 점유하지 않는다.
 *     각 서비스 메서드가 자체 @Transactional을 관리한다.
 *   - Cache Decomposition: 목록 캐시(ID 리스트)와 상세 캐시(ProductDetailResult)를 분리.
 *     상품 정보 변경 시 상세 캐시만 삭제하면 되므로 무효화 범위가 최소화된다.
 */
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductCacheManager productCacheManager;
    private final ApplicationEventPublisher eventPublisher;

    public ProductFacade(ProductService productService, BrandService brandService,
                         ProductCacheManager productCacheManager,
                         ApplicationEventPublisher eventPublisher) {
        this.productService = productService;
        this.brandService = brandService;
        this.productCacheManager = productCacheManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 고객 상품 상세 조회 — Cache-Aside.
     * @Transactional 없음: 캐시 히트 시 DB 커넥션 미사용.
     */
    public ProductDetailResult getProductDetail(Long productId) {
        Optional<ProductDetailResult> cached = productCacheManager.getProductDetail(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Product product = productService.getDisplayableProduct(productId);
        Brand brand = brandService.getActiveBrand(product.getBrandId());
        ProductDetailResult result = new ProductDetailResult(ProductInfo.from(product), BrandInfo.from(brand));

        productCacheManager.putProductDetail(productId, result);

        // 상품 조회 이벤트 발행 — product_metrics 조회 수 집계 + 유저 행동 로깅 (추후 Kafka 전환)
        // userId는 현재 컨텍스트에서 가져올 수 없으므로 null 허용 (비로그인 조회)
        eventPublisher.publishEvent(new ProductViewedEvent(null, productId));

        return result;
    }

    /**
     * 고객 상품 목록 커서 조회 — Cache Decomposition + 첫 페이지 캐싱.
     *
     * 첫 페이지(cursor=null):
     *   1. 목록 캐시(ID 리스트) 조회
     *   2. MGET으로 상세 캐시 일괄 조회
     *   3. partial miss ID만 DB에서 조회 후 캐시 적재
     *   4. 원래 순서대로 조립
     *
     * 이후 페이지: DB 직접 조회 (캐시 미사용)
     */
    public ProductCursorResult getDisplayableProductsWithCursor(Long brandId, ProductSortType sort, ProductCursor cursor, int size) {
        boolean isFirstPage = (cursor == null);

        if (isFirstPage) {
            Optional<ProductCacheManager.ProductListCache> cachedIds =
                    productCacheManager.getProductListIds(sort, brandId);
            if (cachedIds.isPresent()) {
                return assembleFromCache(cachedIds.get());
            }
        }

        CursorResult<Product> result = productService.getDisplayableProductsWithCursor(brandId, sort, cursor, size);

        List<ProductInfo> productInfos = result.items().stream()
                .map(ProductInfo::from)
                .toList();

        ProductCursorResult cursorResult = new ProductCursorResult(productInfos, result.hasNext(), size);

        if (isFirstPage) {
            cacheFirstPage(result.items(), result.hasNext(), size, sort, brandId);
        }

        return cursorResult;
    }

    /**
     * 캐시된 ID 리스트 + 상세 캐시로 커서 목록 응답 조립.
     * partial miss 발생 시 미스 ID만 DB에서 조회 후 캐시 적재.
     */
    private ProductCursorResult assembleFromCache(ProductCacheManager.ProductListCache listCache) {
        List<Long> productIds = listCache.productIds();

        Map<Long, ProductDetailResult> detailMap = new HashMap<>(
                productCacheManager.getProductDetailBatch(productIds));

        List<Long> missingIds = productIds.stream()
                .filter(id -> !detailMap.containsKey(id))
                .toList();

        if (!missingIds.isEmpty()) {
            detailMap.putAll(loadAndCacheProductDetails(missingIds));
        }

        Map<Long, ProductDetailResult> finalDetailMap = Map.copyOf(detailMap);
        List<ProductInfo> productInfos = productIds.stream()
                .filter(finalDetailMap::containsKey)
                .map(id -> finalDetailMap.get(id).product())
                .toList();

        return new ProductCursorResult(productInfos, listCache.hasNext(), listCache.size());
    }

    /**
     * 첫 페이지 DB 조회 결과를 목록 캐시(ID) + 상세 캐시(상품+브랜드)에 적재.
     */
    private void cacheFirstPage(List<Product> products, boolean hasNext, int size,
                                ProductSortType sort, Long brandId) {
        List<Long> ids = products.stream().map(Product::getId).toList();
        productCacheManager.putProductListIds(sort, brandId,
                new ProductCacheManager.ProductListCache(ids, hasNext, size));

        cacheProductDetails(products);
    }

    /**
     * 상품 목록에 대해 브랜드 정보를 조회하여 상세 캐시에 일괄 적재.
     */
    private void cacheProductDetails(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }
        List<Long> brandIds = products.stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        for (Product product : products) {
            Brand brand = brandMap.get(product.getBrandId());
            if (brand != null) {
                productCacheManager.putProductDetail(product.getId(),
                        new ProductDetailResult(ProductInfo.from(product), BrandInfo.from(brand)));
            }
        }
    }

    /**
     * partial miss ID들을 DB에서 조회하여 상세 캐시에 적재 후 반환.
     */
    private Map<Long, ProductDetailResult> loadAndCacheProductDetails(List<Long> missingIds) {
        List<Product> products = productService.getProductsByIds(missingIds);

        List<Long> brandIds = products.stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        Map<Long, ProductDetailResult> result = new HashMap<>();
        for (Product product : products) {
            Brand brand = brandMap.get(product.getBrandId());
            if (brand != null) {
                ProductDetailResult detail = new ProductDetailResult(
                        ProductInfo.from(product), BrandInfo.from(brand));
                result.put(product.getId(), detail);
                productCacheManager.putProductDetail(product.getId(), detail);
            }
        }
        return result;
    }

    public record ProductDetailResult(ProductInfo product, BrandInfo brand) {}

    public record ProductCursorResult(
            List<ProductInfo> products,
            boolean hasNext,
            int size
    ) {}
}
