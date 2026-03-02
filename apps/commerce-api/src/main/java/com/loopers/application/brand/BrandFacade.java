package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final ProductLikeService productLikeService;

    @Transactional
    public BrandInfo registerBrand(String name, String description, String logoUrl) {
        Brand brand = brandService.register(name, description, logoUrl);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long brandId) {
        Brand brand = brandService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandService.getBrands(pageable).map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo updateBrand(Long brandId, String name, String description, String logoUrl) {
        Brand brand = brandService.updateBrand(brandId, name, description, logoUrl);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        brandService.deleteBrand(brandId);

        List<Long> productIds = productService.getIdsByBrandId(brandId);

        productLikeService.deleteByProductIds(productIds);

        productService.deleteByBrandId(brandId);
    }
}
