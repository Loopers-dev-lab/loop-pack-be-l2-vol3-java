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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;

    // Command

    @Transactional
    public Brand register(BrandCommand.Create command) {
        if (brandRepository.existsByName(command.name())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 브랜드입니다");
        }

        Brand brand = Brand.create(command.name(), command.description());
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand update(Long brandId, BrandCommand.Update command) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다"));

        if (brandRepository.existsByNameAndIdNot(command.name(), brand.getId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 브랜드입니다");
        }

        brand.update(command.name(), command.description());
        return brand;
    }

    @Transactional
    public void delete(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다"));
        brand.delete();
    }

    // Query

    public Brand getBrand(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다"));
    }

    public Page<Brand> findBrands(String name, Boolean deleted, Pageable pageable) {
        return brandRepository.findAll(name, deleted, pageable);
    }

    public Brand getActiveBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다"));
        brand.validateNotDeleted();
        return brand;
    }

    public List<Brand> getBrands(List<Long> brandIds) {
        return brandRepository.findAllByIdIn(brandIds);
    }

    public Page<Brand> findActiveBrands(String name, Pageable pageable) {
        return brandRepository.findAllActive(name, pageable);
    }

    public Map<Long, Brand> getBrandsMapByIds(Set<Long> brandIds) {
        return brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }
}
