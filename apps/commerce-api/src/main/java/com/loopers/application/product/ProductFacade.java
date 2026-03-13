package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeService;
import com.loopers.application.PageResult;
import com.loopers.config.CacheConfig;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductFacade {
    private final BrandService brandService;
    private final ProductService productService;
    private final LikeService likeService;

    @Transactional
    public ProductInfo register(ProductCreateCommand command) {
        brandService.getBrand(command.brandId());
        return productService.register(command);
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCT, key = "#id")
    public ProductInfo getActiveProduct(Long id) {
        ProductInfo product = productService.getActiveProduct(id);
        String brandName = brandService.getBrandNameMap(List.of(product.brand().id()))
                                       .get(product.brand().id());
        return product.withBrand(new ProductInfo.BrandSummary(product.brand().id(), brandName));
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCTS, key = "#brandId + ':' + #sort + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResult<ProductInfo> getActiveProducts(Long brandId, ProductSort sort, Pageable pageable) {
        Page<ProductInfo> products = productService.getActiveProducts(brandId, sort, pageable);
        Set<Long> brandIds = products.stream().map(p -> p.brand().id()).collect(Collectors.toSet());
        Map<Long, String> brandNameMap = brandService.getBrandNameMap(brandIds);
        return PageResult.from(products.map(p -> p.withBrand(
                new ProductInfo.BrandSummary(p.brand().id(), brandNameMap.getOrDefault(p.brand().id(), null))
        )));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCTS, allEntries = true)
    })
    @Transactional
    public void delete(Long id) {
        likeService.deleteAllByProductIds(List.of(id));
        productService.delete(id);
    }
}
