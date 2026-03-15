package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel register(Long brandId, String name, String description, int price, int stockQuantity, String imageUrl) {
        ProductModel product = new ProductModel(brandId, name, description, price, stockQuantity, imageUrl);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAll(Sort sort) {
        return productRepository.findAll(sort);
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getByBrandId(Long brandId, Sort sort) {
        return productRepository.findAllByBrandId(brandId, sort);
    }

    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        ProductModel product = getById(productId);
        product.decreaseStock(quantity);
    }

    @Transactional
    public void decreaseStockWithLock(Long productId, int quantity) {
        ProductModel product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        product.decreaseStock(quantity);
    }

    @Transactional
    public void increaseLikeCount(Long productId) {
        productRepository.increaseLikeCount(productId);
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        productRepository.decreaseLikeCount(productId);
    }
}
