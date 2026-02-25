package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAll(Pageable pageable, ProductSortType sortType) {
        if (sortType == ProductSortType.LIKES_DESC) {
            return productRepository.findAllOrderByLikesDesc(pageable);
        }
        return productRepository.findAll(pageable);
    }

    @Transactional
    public ProductModel register(Long brandId, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        BrandModel brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

        ProductModel product = new ProductModel(brand, name, price, description, stockQuantity, status);
        return productRepository.save(product);
    }

    @Transactional
    public ProductModel update(Long id, Long brandId, String name, Long price, String description, int stockQuantity, ProductStatus status) {
        ProductModel product = productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        BrandModel brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

        product.update(brand, name, price, description, stockQuantity, status);
        return product;
    }

    @Transactional
    public void delete(Long id) {
        ProductModel product = productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        product.delete();
    }

    @Transactional
    public void deductStock(Long id, int quantity) {
        ProductModel product = productRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        product.deductStock(quantity);
    }
}
