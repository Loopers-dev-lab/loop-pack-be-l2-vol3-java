package com.loopers.application.admin.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminBrandAppService {
    private final BrandRepository brandRepository;

    @Transactional
    public Brand create(String name) {
        Brand brand = Brand.create(name);
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand update(Long id, String name) {
        Brand brand = getById(id);
        brand.update(name);
        return brandRepository.save(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = getById(id);
        brand.delete();
        brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Brand getById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Brand> getAll() {
        return brandRepository.findAll();
    }
}
