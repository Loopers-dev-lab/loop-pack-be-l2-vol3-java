package com.loopers.application.brand;

import java.util.List;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public BrandResult createBrand(String name, String logoUrl, String description) {
        if (brandRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new CoreException(ErrorType.ALREADY_EXISTS_BRAND_NAME);
        }
        Brand brand = Brand.create(name, logoUrl, description);
        Brand saved = brandRepository.save(brand);
        return BrandResult.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<BrandResult> getBrands(PageSize pageSize) {
        Slice<Brand> brands = brandRepository.findAllBy(pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));
        return new Page<>(
                brands.getContent()
                        .stream()
                        .map(BrandResult::from)
                        .toList(),
                brands.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public BrandResult getBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        return BrandResult.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandResult getActiveBrand(Long brandId) {
        Brand brand = brandRepository.findByIdAndDeletedAtIsNull(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        return BrandResult.from(brand);
    }

    @Transactional
    public void updateBrand(Long brandId, String newName, String newLogoUrl, String newDescription) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        if (brandRepository.existsByIdNotAndNameAndDeletedAtIsNull(brandId, newName)) {
            throw new CoreException(ErrorType.ALREADY_EXISTS_BRAND_NAME);
        }
        brand.update(newName, newLogoUrl, newDescription);
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        if (brand.isDeleted()) {
            return;
        }
        List<Long> productIds = productRepository.findAllByBrandIdAndDeletedAtIsNull(brandId)
                .stream()
                .map(Product::getId)
                .toList();

        if (!productIds.isEmpty()) {
            likeRepository.deleteAllByProductIdIn(productIds);
            productRepository.softDeleteAllByBrandId(brandId);
        }

        brand.delete();
    }
}
