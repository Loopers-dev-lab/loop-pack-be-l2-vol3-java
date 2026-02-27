package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeService;
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
    private final BrandService brandService;
    private final ProductService productService;
    private final LikeService likeService;

    public ProductInfo register(ProductCreateCommand command) {
        brandService.getBrand(command.brandId());
        return productService.register(command);
    }

    public ProductInfo getActiveProduct(Long id) {
        ProductInfo product = productService.getActiveProduct(id);
        String brandName = brandService.getBrandNameMap(List.of(product.brandId()))
                                       .get(product.brandId());
        return product.withBrandName(brandName);
    }

    public Page<ProductInfo> getActiveProducts(Long brandId, ProductSort sort, Pageable pageable) {
        Page<ProductInfo> products = productService.getActiveProducts(brandId, sort, pageable);
        Set<Long> brandIds = products.stream().map(ProductInfo::brandId).collect(Collectors.toSet());
        Map<Long, String> brandNameMap = brandService.getBrandNameMap(brandIds);
        return products.map(p -> p.withBrandName(brandNameMap.getOrDefault(p.brandId(), null)));
    }

    public ProductInfo getProduct(Long id) {
        return productService.getProduct(id);
    }

    public Page<ProductInfo> getAllProducts(Long brandId, Pageable pageable) {
        return productService.getAllProducts(brandId, pageable);
    }

    public ProductInfo update(Long id, ProductUpdateCommand command) {
        return productService.update(id, command);
    }

    @Transactional
    public void delete(Long id) {
        likeService.deleteAllByProductIds(List.of(id));
        productService.delete(id);
    }
}
