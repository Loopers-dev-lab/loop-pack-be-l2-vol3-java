package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrandValidator {

    private final BrandRepository brandRepository;

    public void validateNameNotDuplicated(String name) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.BRAND_ALREADY_EXISTS);
        }
    }

    public void validateNameNotDuplicatedExcept(String name, Long excludeId) {
        if (brandRepository.existsByNameAndIdNot(name, excludeId)) {
            throw new CoreException(ErrorType.BRAND_ALREADY_EXISTS);
        }
    }
}
