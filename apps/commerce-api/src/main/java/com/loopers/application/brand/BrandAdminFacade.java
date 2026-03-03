package com.loopers.application.brand;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BrandAdminFacade {

    private final BrandApplicationService brandApplicationService;
    private final ProductApplicationService productApplicationService;
    private final LikeApplicationService likeApplicationService;

    public void delete(UUID brandId) {
        List<UUID> productIds = productApplicationService.findActiveProductIdsByBrandId(brandId);
        likeApplicationService.deleteByProductIds(productIds);
        productApplicationService.deleteSoftByBrandId(brandId);
        brandApplicationService.delete(brandId);
    }
}
