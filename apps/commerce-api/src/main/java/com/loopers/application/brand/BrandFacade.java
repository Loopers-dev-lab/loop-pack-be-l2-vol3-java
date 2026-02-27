package com.loopers.application.brand;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BrandFacade {
    private final BrandApplicationService brandService;
    private final ProductApplicationService productService;
    private final LikeApplicationService likeService;

    public BrandInfo register(String name, String description) {
        return brandService.register(name, description);
    }

    public BrandInfo getBrand(Long id) {
        return brandService.getBrand(id);
    }

    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandService.getBrands(pageable);
    }

    public BrandInfo update(Long id, String name, String description) {
        return brandService.update(id, name, description);
    }

    @Transactional
    public void delete(Long brandId) {
        List<Long> productIds = productService.getProductIdsByBrandId(brandId);
        likeService.deleteAllByProductIds(productIds);
        productService.deleteAllByBrandId(brandId);
        brandService.delete(brandId);
    }
}
