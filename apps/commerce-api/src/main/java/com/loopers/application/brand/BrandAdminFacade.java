package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandAdminFacade {
    private final BrandService brandService;
    private final ProductService productService;

    // 브랜드 등록
    public BrandInfo register(BrandRegisterCommand command){
        Brand brand = brandService.register(command.name());
        return BrandInfo.from(brand);
    }

    // 브랜드 상세 조회
    public BrandInfo findById(Long id){
        Brand brand = brandService.findById(id);
        return BrandInfo.from(brand);
    }

    // 브랜드 목록 조회
    public Page<BrandInfo> findAll(Pageable pageable){
        return brandService.findAll(pageable).map(BrandInfo::from);
    }

    // 브랜드 정보 수정
    public BrandInfo update(BrandUpdateCommand command){
        Brand brand = brandService.update(command.id(), command.name());
        return BrandInfo.from(brand);
    }

    // 브랜드 삭제 - 상품 cascade soft delete 후 브랜드 삭제 (US-B06)
    // Like/Cart cascade는 해당 도메인 구현 시 추가 예정
    public void delete(Long id){
        brandService.findById(id); // 브랜드 존재 확인
        productService.deleteAllByBrandId(id);
        brandService.delete(id);
    }
}
