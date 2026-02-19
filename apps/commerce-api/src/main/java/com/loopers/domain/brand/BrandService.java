package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class BrandService {
    private final BrandRepository brandRepository;

    @Transactional
    public Brand register(String name, String description) {
        Brand brand = Brand.create(name, description);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Brand getBrand(Long id) {
        Brand brand = findById(id);

        if (brand.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[brandId = " + id + "] 를 찾을 수 없습니다.");
        }

        return brand;
    }

    @Transactional(readOnly = true)
    public Page<Brand> getBrands(Pageable pageable) {
        return brandRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional
    public Brand update(Long id, String name, String description) {
        Brand brand = getBrand(id);
        brand.update(name, description);

        return brand;
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = findById(id);
        brand.delete();
    }

    private Brand findById(Long id) {
        return brandRepository.findById(id)
                              .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                                                   "[brandId = " + id + "] 를 찾을 수 없습니다."));
    }
}
