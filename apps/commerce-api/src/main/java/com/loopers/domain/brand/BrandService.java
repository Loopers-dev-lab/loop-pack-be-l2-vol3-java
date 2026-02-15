package com.loopers.domain.brand;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand register(String name) {
        return brandRepository.save(new Brand(name));
    }

    public Brand getById(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    public Map<Long, Brand> getByIds(Set<Long> ids) {
        List<Brand> brands = brandRepository.findAllByIds(ids);
        return brands.stream().collect(Collectors.toMap(Brand::getId, brand -> brand));
    }

    public PageResult<Brand> getAll(int page, int size) {
        return brandRepository.findAll(page, size);
    }

    public Brand update(Long id, String name) {
        Brand brand = getById(id);
        brand.update(name);
        return brandRepository.save(brand);
    }

    public void delete(Long id) {
        Brand brand = getById(id);
        brand.delete();
        brandRepository.save(brand);
    }
}
