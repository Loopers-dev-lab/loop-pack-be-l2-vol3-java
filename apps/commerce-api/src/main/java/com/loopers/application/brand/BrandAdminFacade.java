package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandAdminFacade {
    private final BrandService brandService;

    // 브랜드 등록
    public BrandInfo register(String name){
        Brand brand = brandService.register(name);
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

    // 브랜드 삭제
    public void delete(Long id){
        brandService.delete(id);
    }
}
