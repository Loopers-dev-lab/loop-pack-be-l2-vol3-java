package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductLikeService productLikeService;
    private final ProductCacheService productCacheService;

    @Transactional
    public ProductInfo register(Long brandId, String name, String description, int price, int stockQuantity, String imageUrl) {
        brandService.getById(brandId);
        ProductModel product = productService.register(brandId, name, description, price, stockQuantity, imageUrl);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public ProductDetailInfo getById(Long id) {
        // 1. 캐시 조회
        Optional<ProductDetailInfo> cached = productCacheService.getProductDetail(id);
        if (cached.isPresent()) {
            return cached.get();   // 캐시 히트 → DB 안 가고 바로 반환
        }

        // 2. 캐시 미스 → DB 조회
        ProductModel product = productService.getById(id);
        BrandModel brand = brandService.getById(product.getBrandId());
        ProductDetailInfo info = ProductDetailInfo.of(product, brand, product.getLikeCount());

        // 3. 다음을 위해 캐시에 저장
        productCacheService.setProductDetail(id, info);

        return info;
    }

    @Transactional(readOnly = true)
    public List<ProductDetailInfo> getProducts(ProductSortType sortType) {
        // 1. 캐시 조회
        Optional<List<ProductDetailInfo>> cached = productCacheService.getProductList(sortType.name(), null);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. 캐시 미스 → DB 조회
        List<ProductModel> products = productService.getAll(sortType.toSort());

        // 브랜드 ID 수집 → 한 번에 조회
        Set<Long> brandIds = products.stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, BrandModel> brandMap = brandService.getByIds(brandIds);

        // 조회 결과로 조립
        List<ProductDetailInfo> result = products.stream()
                .map(product -> {
                    BrandModel brand = brandMap.get(product.getBrandId());
                    return ProductDetailInfo.of(product, brand, product.getLikeCount());
                })
                .toList();

        // 3. 캐시 저장
        productCacheService.setProductList(sortType.name(), null, result);

        return result;
    }

    @Transactional(readOnly = true)
    public List<ProductDetailInfo> getProductsByBrandId(Long brandId, ProductSortType sortType) {
        // 1. 캐시 조회
        Optional<List<ProductDetailInfo>> cached = productCacheService.getProductList(sortType.name(), brandId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. 캐시 미스 → DB 조회
        List<ProductModel> products = productService.getByBrandId(brandId, sortType.toSort());
        BrandModel brand = brandService.getById(brandId);

        List<ProductDetailInfo> result = products.stream()
                .map(product -> ProductDetailInfo.of(product, brand, product.getLikeCount()))
                .toList();

        // 3. 캐시 저장
        productCacheService.setProductList(sortType.name(), brandId, result);

        return result;
    }
}
