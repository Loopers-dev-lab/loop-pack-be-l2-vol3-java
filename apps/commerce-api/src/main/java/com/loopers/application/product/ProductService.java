package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductService {
    private final ProductRepository productRepository;

    @Transactional
    public ProductInfo register(ProductCreateCommand command) {
        Product product = Product.create(command.brandId(), command.name(), command.description(), command.price(), command.stockQuantity());
        return ProductInfo.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(findNonDeletedById(id));
    }

    @Transactional(readOnly = true)
    public ProductInfo getVisibleProduct(Long id) {
        Product product = findNonDeletedById(id);
        if (product.getVisibility() == Product.Visibility.HIDDEN) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 를 찾을 수 없습니다.");
        }
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getVisibleProducts(Long brandId, ProductSort sort, Pageable pageable) {
        return productRepository.findVisibleProducts(brandId, sort.toOrder(), pageable).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getAllProducts(Long brandId, Pageable pageable) {
        return productRepository.findAllProducts(brandId, pageable).map(ProductInfo::from);
    }

    @Transactional
    public ProductInfo update(Long id, ProductUpdateCommand command) {
        Product product = findNonDeletedById(id);
        product.update(command.name(), command.description(), command.price(), command.stockQuantity());
        if (command.visibility() != null) {
            product.changeVisibility(command.visibility());
        }
        return ProductInfo.from(product);
    }

    @Transactional
    public ProductInfo changeVisibility(Long id, Product.Visibility visibility) {
        Product product = findNonDeletedById(id);
        product.changeVisibility(visibility);
        return ProductInfo.from(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.delete();
    }

    @Transactional(readOnly = true)
    public List<Long> getProductIdsByBrandId(Long brandId) {
        return productRepository.findAllByBrandIdAndDeletedAtIsNull(brandId)
                                .stream()
                                .map(Product::getId)
                                .toList();
    }

    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
        products.forEach(Product::delete);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getVisibleProductsByIds(List<Long> ids) {
        return productRepository.findAllByIdInAndDeletedAtIsNull(ids)
                                .stream()
                                .filter(p -> p.getVisibility() == Product.Visibility.VISIBLE)
                                .map(ProductInfo::from)
                                .toList();
    }

    private Product findNonDeletedById(Long id) {
        Product product = findById(id);
        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 를 찾을 수 없습니다.");
        }
        return product;
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                        "[productId = " + id + "] 를 찾을 수 없습니다."));
    }
}
