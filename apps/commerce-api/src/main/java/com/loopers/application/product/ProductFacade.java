package com.loopers.application.product;

import com.loopers.config.CacheProperties;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductCacheRepository productCacheRepository;
    private final CacheProperties cacheProperties;

    // 상품 상세 조회
    @Transactional(readOnly = true)
    public ProductInfo findById(Long id) {
        Product product = productService.findById(id);
        String brandName = brandService.findById(product.getBrandId()).getName();
        return ProductInfo.from(product, brandName);
    }

    // 상품 목록 조회 (Cache-Aside 패턴)
    @Transactional(readOnly = true)
    public ProductPageResult findAll(Long brandId, Pageable pageable) {
        // 딥 페이징 구간은 캐시 미적용 → DB 직접 조회
        if (!cacheProperties.isCacheable(pageable.getPageNumber())) {
            return fetchFromDb(brandId, pageable);
        }

        String cacheKey = buildCacheKey(brandId, pageable);

        // 캐시 히트: 즉시 반환
        return productCacheRepository.getList(cacheKey).orElseGet(() -> {
            // 캐시 미스: DB 조회 후 캐시에 저장
            ProductPageResult result = fetchFromDb(brandId, pageable);
            productCacheRepository.saveList(cacheKey, result);
            return result;
        });
    }

    private ProductPageResult fetchFromDb(Long brandId, Pageable pageable) {
        Page<Product> products = productService.findAll(brandId, pageable);
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);
        Page<ProductInfo> infoPage = products.map(
                product -> ProductInfo.from(product, brandNameMap.get(product.getBrandId()))
        );
        return ProductPageResult.from(infoPage);
    }

    /**
     * 캐시 키 레이어링 전략: {서비스}:{도메인}:{오퍼레이션}:{파라미터}
     * 예) loopers:product:list:brand=1:sort=createdAt_DESC:page=0:size=20
     */
    private String buildCacheKey(Long brandId, Pageable pageable) {
        String brandPart = brandId == null ? "brand=all" : "brand=" + brandId;
        String sortPart = pageable.getSort().stream()
                .map(order -> order.getProperty() + "_" + order.getDirection())
                .collect(Collectors.joining(","));
        return String.format("loopers:product:list:%s:sort=%s:page=%d:size=%d",
                brandPart, sortPart, pageable.getPageNumber(), pageable.getPageSize());
    }
}
