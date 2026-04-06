package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Product getProductById(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다"));
    }

    @Transactional(readOnly = true)
    public Page<Product> getProducts(Long brandId, Pageable pageable) {
        if (brandId != null) {
            return productRepository.findByBrandId(brandId, pageable);
        }
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByIds(List<Long> ids) {
        return productRepository.findAllByIdIn(ids);
    }
}
