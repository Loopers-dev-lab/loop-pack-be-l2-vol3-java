package com.loopers.application.brand;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BrandFacade {
    private final BrandApplicationService brandService;
    private final ProductApplicationService productService;
    private final LikeApplicationService likeService;

    @Transactional
    public void delete(Long brandId) {
        List<Long> productIds = productService.getProductIdsByBrandId(brandId);
        likeService.deleteAllByProductIds(productIds);
        productService.deleteAllByBrandId(brandId);
        brandService.delete(brandId);
    }
}
