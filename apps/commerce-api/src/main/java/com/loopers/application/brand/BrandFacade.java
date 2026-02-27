package com.loopers.application.brand;

import com.loopers.application.brand.dto.FindBrandListResDto;
import com.loopers.application.brand.dto.FindBrandResDto;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public FindBrandResDto findBrand(Long brandId) {
        Brand brand = brandService.findBrand(brandId);
        List<Product> products = productService.findProductsByBrandId(brandId);
        return FindBrandResDto.of(brand, products);
    }

    public Page<FindBrandListResDto> findBrandList(Pageable pageable) {
        return brandService.findBrandList(pageable).map(FindBrandListResDto::from);
    }

}
