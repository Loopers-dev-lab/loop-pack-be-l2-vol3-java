package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ProductErrorType;
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(Long brandId, String name, String description, int basePrice) {
        Product product = Product.create(brandId, name, description, basePrice);
        return productRepository.save(product);
    }

    public Product getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ProductErrorType.PRODUCT_NOT_FOUND));
        if (product.getDeletedAt() != null) {
            throw new CoreException(ProductErrorType.ALREADY_DELETED);
        }
        return product;
    }

    public Product getDisplayableProduct(Long id) {
        Product product = getById(id);
        if (!product.isDisplayable()) {
            throw new CoreException(ProductErrorType.NOT_DISPLAYABLE);
        }
        return product;
    }

    public void delete(Long id) {
        Product product = getById(id);
        product.delete();
    }

    public void incrementLikeCount(Long id) {
        Product product = getById(id);
        product.incrementLikeCount();
    }

    public void decrementLikeCount(Long id) {
        Product product = getById(id);
        product.decrementLikeCount();
    }
}
