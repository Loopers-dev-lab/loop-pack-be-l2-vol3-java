package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandFacade {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;

    public Brand getBrand(Long brandId) {
        return brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    @Transactional
    public Brand createBrand(String name, String description) {
        return brandRepository.save(new Brand(name, description));
    }

    @Transactional
    public Brand updateBrand(Long brandId, String name, String description) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.changeName(name);
        brand.changeDescription(description);
        return brand;
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        List<Product> products = productRepository.findAllByBrandId(brandId);
        for (Product product : products) {
            likeRepository.deleteAllByProductId(product.getId());
            product.delete();
        }
        brand.delete();
    }
}
