package com.loopers.application.product;

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

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    // 상품 상세 조회
    @Transactional(readOnly = true)
    public ProductInfo findById(Long id) {
        Product product = productService.findById(id);
        String brandName = brandService.findById(product.getBrandId()).getName();
        return ProductInfo.from(product, brandName);
    }

    // 상품 목록 조회 (brandId 필터 선택)
    @Transactional(readOnly = true)
    public Page<ProductInfo> findAll(Long brandId, Pageable pageable) {
        Page<Product> products = productService.findAll(brandId, pageable);
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);
        return products.map(product -> ProductInfo.from(product, brandNameMap.get(product.getBrandId())));
    }
}
