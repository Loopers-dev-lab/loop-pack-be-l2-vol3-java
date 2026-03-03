package com.loopers.application.brand;

import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BrandFacade {
    private final BrandService brandService;
    private final ProductService productService;
    private final LikeService likeService;

    @Transactional
    public void delete(Long brandId) {
        List<Long> productIds = productService.getProductIdsByBrandId(brandId);
        likeService.deleteAllByProductIds(productIds);
        productService.deleteAllByBrandId(brandId);
        brandService.delete(brandId);
    }
}
