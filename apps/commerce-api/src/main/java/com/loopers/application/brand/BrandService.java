package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandModel register(String name, String description) {
        brandRepository.findByName(name).ifPresent(existing -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다: " + name);
        });
        return brandRepository.save(new BrandModel(name, description));
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long brandId) {
        BrandModel brand = findById(brandId);
        if (brand.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        return brand;
    }

    @Transactional(readOnly = true)
    public BrandModel getBrandForAdmin(Long brandId) {
        return findById(brandId);
    }

    @Transactional
    public BrandModel update(Long brandId, String name, String description) {
        BrandModel brand = findById(brandId);

        if (!brand.getName().equals(name)) {
            brandRepository.findByName(name).ifPresent(existing -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
            });
        }

        brand.update(name, description);
        return brand;
    }

    @Transactional
    public void delete(Long brandId) {
        BrandModel brand = findById(brandId);
        brand.delete();
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getAll(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Map<Long, BrandModel> getByIds(List<Long> ids) {
        return brandRepository.findAllByIdIn(ids)
            .stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }

    private BrandModel findById(Long brandId) {
        return brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다. [id = " + brandId + "]"));
    }
}
