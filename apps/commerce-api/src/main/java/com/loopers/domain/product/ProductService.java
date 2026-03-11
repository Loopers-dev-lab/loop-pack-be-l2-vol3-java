package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> createProducts(Map<Long, CreateProductRequest> createProductsCommand) {
        if (createProductsCommand == null || createProductsCommand.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.CREATE_PRODUCT_REQUEST_REQUIRED);
        }

        List<Product> createdProducts = new ArrayList<>();

        createProductsCommand.forEach((brandId, request) -> {
            Product product = Product.create(
                null,
                request.name(),
                brandId,
                request.price(),
                request.stock(),
                0
            );
            Product savedProduct = productRepository.save(product);
            createdProducts.add(savedProduct);
        });

        return createdProducts;
    }

    public void increaseLikeCount(Long productId) {
        Product product = getById(productId);
        product.increaseLikeCount();
        productRepository.update(product);
    }

    public void decreaseLikeCount(Long productId) {
        Product product = getById(productId);
        product.decreaseLikeCount();
        productRepository.update(product);
    }

    public void decreaseStock(Long productId, Integer decreaseStock) {
        Product product = getById(productId);
        product.decreaseStock(decreaseStock);
        productRepository.update(product);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.PRODUCT_NOT_FOUND));
    }

    public List<Product> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.PRODUCT_ID_LIST_REQUIRED);
        }
        List<Product> products = productRepository.findByIds(ids);
        if (products.size() != ids.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.PRODUCT_ID_LIST_CONTAINS_INVALID);
        }
        return products;
    }

    public List<Product> findProducts(ProductSearchCondition condition) {
        if (condition == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Product.SEARCH_CONDITION_REQUIRED);
        }
        return productRepository.findAll(condition);
    }
}
