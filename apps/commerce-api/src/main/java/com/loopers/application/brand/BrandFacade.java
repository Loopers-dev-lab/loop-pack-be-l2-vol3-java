package com.loopers.application.brand;

import com.loopers.application.product.ProductService;
import com.loopers.domain.brand.Brand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    // Command

    @Transactional
    public BrandInfo register(@Valid BrandRequest.Register request) {
        BrandCommand.Create command = BrandCommand.Create.of(request.name(), request.description());
        Brand brand = brandService.register(command);
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo update(Long brandId, @Valid BrandRequest.Update request) {
        BrandCommand.Update command = BrandCommand.Update.of(request.name(), request.description());
        Brand brand = brandService.update(brandId, command);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void delete(Long brandId) {
        brandService.delete(brandId);
        productService.deleteAllByBrandId(brandId);
    }

    // Query

    public Page<BrandInfo> getList(@Valid BrandRequest.ListAll request) {
        Page<Brand> brands = brandService.findBrands(request.name(), request.toDeleted(), request.toPageable());
        return brands.map(BrandInfo::from);
    }

    public BrandInfo getDetail(Long brandId) {
        Brand brand = brandService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    public Page<BrandInfo> getActiveList(@Valid BrandRequest.ListActive request) {
        Page<Brand> brands = brandService.findActiveBrands(request.name(), request.toPageable());
        return brands.map(BrandInfo::from);
    }

    public BrandInfo getActiveDetail(Long brandId) {
        Brand brand = brandService.getActiveBrand(brandId);
        return BrandInfo.from(brand);
    }
}
