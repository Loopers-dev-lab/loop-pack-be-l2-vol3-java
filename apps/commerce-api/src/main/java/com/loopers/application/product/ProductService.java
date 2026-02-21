package com.loopers.application.product;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public Long createProduct(ProductCommand.CreateProductCommand command) {
        if (!brandRepository.existsByIdAndDeletedAtIsNull(command.brandId())) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }
        Product product = Product.create(
                command.brandId(),
                command.name(),
                command.thumbnailUrl(),
                command.price(),
                command.stock(),
                command.description()
        );
        Product saved = productRepository.save(product);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProducts(PageSize pageSize) {
        Slice<Product> products = productRepository.findAllBy(pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));
        return new Page<>(
                products.map(ProductResult::from)
                        .stream()
                        .toList(),
                products.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProductsByBrandId(Long brandId, PageSize pageSize) {
        if (!brandRepository.existsById(brandId)) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }
        Slice<Product> products = productRepository.findAllByBrandId(brandId, pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));
        return new Page<>(
                products.map(ProductResult::from)
                        .stream()
                        .toList(),
                products.hasNext()
        );
    }
}
