package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BrandApplicationService {
    private final BrandRepository brandRepository;

    @Transactional
    public BrandInfo register(String name, String description) {
        Brand brand = Brand.create(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        Brand brand = findById(id);
        if (brand.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[brandId = " + id + "] 를 찾을 수 없습니다.");
        }
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandRepository.findAllByDeletedAtIsNull(pageable).map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo update(Long id, String name, String description) {
        Brand brand = findNonDeletedById(id);
        brand.update(name, description);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getBrandNameMap(Collection<Long> brandIds) {
        return brandRepository.findAllByIdIn(brandIds).stream()
                              .collect(Collectors.toMap(Brand::getId, Brand::getName));
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = findById(id);
        brand.delete();
    }

    private Brand findNonDeletedById(Long id) {
        Brand brand = findById(id);
        if (brand.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[brandId = " + id + "] 를 찾을 수 없습니다.");
        }
        return brand;
    }

    private Brand findById(Long id) {
        return brandRepository.findById(id)
                              .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                                                   "[brandId = " + id + "] 를 찾을 수 없습니다."));
    }
}
