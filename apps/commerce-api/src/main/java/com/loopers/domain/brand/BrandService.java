package com.loopers.domain.brand;

import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductService productService;

    public BrandService(BrandRepository brandRepository, ProductService productService) {
        this.brandRepository = brandRepository;
        this.productService = productService;
    }

    @Transactional
    public BrandModel register(String name) {
        BrandModel brand = BrandModel.create(name);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Optional<BrandModel> findById(Long id) {
        return brandRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<BrandModel> findByIdAndNotDeleted(Long id) {
        return brandRepository.findByIdAndNotDeleted(id);
    }

    @Transactional(readOnly = true)
    public Map<Long, BrandModel> findByIdAndNotDeletedIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<BrandModel> list = brandRepository.findByIdAndNotDeletedIn(ids);
        return list.stream().collect(Collectors.toMap(BrandModel::getId, b -> b));
    }

    @Transactional
    public BrandModel update(Long id, String name) {
        BrandModel brand = brandRepository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: " + id));
        try {
            brand.updateName(name);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage(), e);
        }
        return brandRepository.save(brand);
    }

    @Transactional
    public void delete(Long id) {
        BrandModel brand = brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: " + id));
        productService.softDeleteByBrandId(id);
        brand.delete();
        brandRepository.save(brand);
    }
}
