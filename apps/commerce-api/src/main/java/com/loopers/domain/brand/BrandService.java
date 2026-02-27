package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand register(String name) {
        BrandName brandName = new BrandName(name);
        if (brandRepository.existsByName(brandName)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 브랜드 이름입니다");
        }
        Brand brand = new Brand(name);
        return brandRepository.save(brand);
    }
}
