package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandAdminFacade {
    private final BrandService brandService;
    private final ProductService productService;
    private final LikeService likeService;

    // 브랜드 등록
    @Transactional
    public BrandInfo register(BrandRegisterCommand command){
        Brand brand = brandService.register(command.name());
        return BrandInfo.from(brand);
    }

    // 브랜드 상세 조회
    @Transactional(readOnly = true)
    public BrandInfo findById(Long id){
        Brand brand = brandService.findById(id);
        return BrandInfo.from(brand);
    }

    // 브랜드 목록 조회
    @Transactional(readOnly = true)
    public Page<BrandInfo> findAll(Pageable pageable){
        return brandService.findAll(pageable).map(BrandInfo::from);
    }

    // 브랜드 정보 수정
    @Transactional
    public BrandInfo update(BrandUpdateCommand command){
        Brand brand = brandService.update(command.id(), command.name());
        return BrandInfo.from(brand);
    }

    /**
     * 브랜드 삭제 (US-B06)
     * 좋아요(hard delete) → 상품(soft delete) → 브랜드(soft delete) 순서로 처리
     */
    @Transactional
    public void delete(Long id){
        Brand brand = brandService.findById(id); // 브랜드 존재 확인
        // 브랜드에 속한 상품 ID 조회 (좋아요 cascade 삭제 전 필요)
        List<Long> productIds = productService.findIdsByBrandId(id);
        // 좋아요 cascade hard delete
        likeService.deleteAllByProductIds(productIds);
        // 상품 cascade soft delete
        productService.deleteAllByBrandId(id);
        // 브랜드 soft delete
        brand.delete();
    }
}
