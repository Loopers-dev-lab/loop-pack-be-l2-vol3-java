package com.loopers.application.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.like.LikeApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final BrandApplicationService brandService;
    private final ProductApplicationService productService;
    private final LikeApplicationService likeService;

    @Transactional
    public ProductInfo register(ProductCreateCommand command) {
        brandService.getBrand(command.brandId());
        return productService.register(command);
    }

    public ProductInfo getActiveProduct(Long id) {
        ProductInfo product = productService.getActiveProduct(id);
        String brandName = brandService.getBrandNameMap(List.of(product.brand().id()))
                                       .get(product.brand().id());
        return product.withBrand(new ProductInfo.BrandSummary(product.brand().id(), brandName));
    }

    public Page<ProductInfo> getActiveProducts(Long brandId, ProductSort sort, Pageable pageable) {
        Page<ProductInfo> products = productService.getActiveProducts(brandId, sort, pageable);
        Set<Long> brandIds = products.stream().map(p -> p.brand().id()).collect(Collectors.toSet());
        Map<Long, String> brandNameMap = brandService.getBrandNameMap(brandIds);
        return products.map(p -> p.withBrand(
                new ProductInfo.BrandSummary(p.brand().id(), brandNameMap.getOrDefault(p.brand().id(), null))
        ));
    }

    @Transactional
    public void delete(Long id) {
        likeService.deleteAllByProductIds(List.of(id));
        productService.delete(id);
    }
}
