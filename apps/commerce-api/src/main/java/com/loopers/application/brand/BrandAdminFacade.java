package com.loopers.application.brand;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BrandAdminFacade {

    private final BrandApplicationService brandApplicationService;
    private final ProductApplicationService productApplicationService;
    private final LikeApplicationService likeApplicationService;

    public void delete(Long brandId) {
        List<Long> productIds = productApplicationService.findActiveProductIdsByBrandId(brandId);
        likeApplicationService.deleteByProductIds(productIds);
        productApplicationService.deleteSoftByBrandId(brandId);
        brandApplicationService.delete(brandId);
    }
}
