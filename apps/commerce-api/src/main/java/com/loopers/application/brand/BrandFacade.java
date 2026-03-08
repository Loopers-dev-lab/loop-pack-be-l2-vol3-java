package com.loopers.application.brand;

import com.loopers.domain.brand.*;
import com.loopers.domain.brand.exception.BrandNotDeletedException;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final BrandService brandService;

    @Transactional
    public BrandDto.BrandInfo register(String name, String description) {
        BrandName brandName = new BrandName(name);
        BrandDescription brandDescription = new BrandDescription(description);

        if (brandRepository.existsByName(brandName.value())) {
            throw new CoreException(ErrorType.DUPLICATE_BRAND_NAME);
        }

        Brand brand = brandService.createBrand(brandName, brandDescription);
        Brand savedBrand = brandRepository.save(brand);
        return BrandDto.BrandInfo.from(savedBrand);
    }

    @Transactional(readOnly = true)
    public Page<BrandDto.BrandInfo> getAdminBrands(Pageable pageable) {
        return brandRepository.findAll(pageable)
            .map(BrandDto.BrandInfo::from);
    }

    @Transactional(readOnly = true)
    public BrandDto.BrandInfo getBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        return BrandDto.BrandInfo.from(brand);
    }

    @Transactional
    public BrandDto.BrandInfo update(Long brandId, String name, String description) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        BrandName brandName = new BrandName(name);
        BrandDescription brandDescription = new BrandDescription(description);

        if (!brand.getName().equals(brandName.value()) && brandRepository.existsByName(brandName.value())) {
            throw new CoreException(ErrorType.DUPLICATE_BRAND_NAME);
        }

        brandService.updateBrand(brand, brandName, brandDescription);
        Brand savedBrand = brandRepository.save(brand);
        return BrandDto.BrandInfo.from(savedBrand);
    }

    @Transactional
    public void delete(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        brandService.deleteBrand(brand);
        brandRepository.save(brand);

        for (Product product : productRepository.findAllByBrandId(brandId)) {
            product.delete();
            productRepository.save(product);
        }
    }

    @Transactional
    public BrandDto.BrandInfo restore(Long brandId) {
        Brand brand = brandRepository.findByIdIncludingDeleted(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        try {
            brandService.restoreBrand(brand, brandId);
        } catch (BrandNotDeletedException e) {
            throw new CoreException(ErrorType.BRAND_NOT_DELETED);
        }
        Brand savedBrand = brandRepository.save(brand);

        for (Product product : productRepository.findAllByBrandIdIncludingDeleted(brandId)) {
            if (!product.isDeleted()) {
                continue;
            }
            product.restore();
            productRepository.save(product);
        }

        return BrandDto.BrandInfo.from(savedBrand);
    }
}
