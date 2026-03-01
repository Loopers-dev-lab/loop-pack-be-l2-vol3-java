package com.loopers.application.brand;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.like.LikeService;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 브랜드를 삭제합니다.
 *
 * <p>브랜드를 소프트 삭제하고, 해당 브랜드의 모든 상품과 관련 좋아요를 함께 삭제합니다.
 * 이미 삭제된 브랜드인 경우 아무 작업도 수행하지 않습니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class DeleteBrandUseCase {

    private final BrandService brandService;
    private final ProductService productService;
    private final LikeService likeService;

    /**
     * @param brandId 삭제할 브랜드 ID
     */
    @Transactional
    public void execute(Long brandId) {
        boolean deleted = brandService.delete(brandId);
        if (!deleted) {
            return;
        }
        List<Long> productIds = productService.getActiveProductIdsByBrandId(brandId);
        productService.softDeleteAllByBrandId(brandId);
        likeService.deleteLikesByProductIds(productIds);
    }
}
