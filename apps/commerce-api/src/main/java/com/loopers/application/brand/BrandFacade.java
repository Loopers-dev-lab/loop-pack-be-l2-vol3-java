package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

    public BrandInfo getBrand(Long id) {
        BrandModel brand = brandService.getBrand(id);
        return BrandInfo.from(brand);
    }

    public Page<BrandInfo> getAll(Pageable pageable) {
        return brandService.getAll(pageable).map(BrandInfo::from);
    }

    public BrandInfo register(String name, String description) {
        BrandModel brand = brandService.register(name, description);
        return BrandInfo.from(brand);
    }

    public BrandInfo update(Long id, String name, String description) {
        BrandModel brand = brandService.update(id, name, description);
        return BrandInfo.from(brand);
    }

    public void delete(Long id) {
        brandService.delete(id);
    }
}
