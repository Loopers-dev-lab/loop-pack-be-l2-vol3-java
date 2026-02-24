package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductAdminFacade {

    private final ProductService productService;
    private final BrandService brandService;

    // 상품 등록 - 브랜드 존재 확인은 Facade 책임 (BR-P01, US-P05)
    public ProductInfo register(ProductRegisterCommand command) {
        Brand brand = brandService.findById(command.brandId()); // 브랜드 미존재 시 NOT_FOUND 예외
        Product product = productService.register(
                command.brandId(), command.name(), command.price(), command.stock());
        return ProductInfo.from(product, brand.getName());
    }

    // 상품 상세 조회
    public ProductInfo findById(Long id) {
        Product product = productService.findById(id);
        String brandName = brandService.findById(product.getBrandId()).getName();
        return ProductInfo.from(product, brandName);
    }

    // 상품 목록 조회 (brandId 필터 선택)
    public Page<ProductInfo> findAll(Long brandId, Pageable pageable) {
        Page<Product> products = productService.findAll(brandId, pageable);
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);
        return products.map(product -> ProductInfo.from(product, brandNameMap.get(product.getBrandId())));
    }

    // 상품 정보 수정
    public ProductInfo update(ProductUpdateCommand command) {
        Product product = productService.update(
                command.id(), command.name(), command.price(), command.stock());
        String brandName = brandService.findById(product.getBrandId()).getName();
        return ProductInfo.from(product, brandName);
    }

    // 상품 삭제
    public void delete(Long id) {
        productService.delete(id);
    }
}
