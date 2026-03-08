package com.loopers.domain.brand;

import com.loopers.domain.brand.exception.BrandNotDeletedException;
import org.springframework.stereotype.Component;

/**
 * Brand 도메인 서비스
 * 엔티티 단독으로 두기 애매한 브랜드 도메인 규칙을 담당한다.
 */
@Component
public class BrandService {

    public Brand createBrand(BrandName name, BrandDescription description) {
        return Brand.create(name, description);
    }

    public void updateBrand(Brand brand, BrandName name, BrandDescription description) {
        brand.update(name, description);
    }

    public void deleteBrand(Brand brand) {
        brand.delete();
    }

    public void restoreBrand(Brand brand, Long brandId) {
        if (!brand.isDeleted()) {
            throw new BrandNotDeletedException(brandId);
        }
        brand.restore();
    }

}
