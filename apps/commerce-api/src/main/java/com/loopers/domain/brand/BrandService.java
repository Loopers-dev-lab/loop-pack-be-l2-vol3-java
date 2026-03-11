package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand create(String name, String description) {
        Brand brand = Brand.create(null, name, description);
        return  brandRepository.create(brand);
    }

    public Brand update(Long id, String name, String description) {
        Brand brand = getById(id);
        brand.update(name, description);
        return brandRepository.update(brand);
    }

    public Brand getById(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Brand.BRAND_NOT_FOUND));
    }
}
