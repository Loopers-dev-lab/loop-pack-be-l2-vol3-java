package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
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
    private final BrandService brandService;

    @Transactional
    public Product register(ProductCreateCommand command) {
        brandService.getBrand(command.brandId());
        Product product = Product.create(command.brandId(), command.name(), command.description(), command.price(), command.stockQuantity());
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        Product product = findById(id);
        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 를 찾을 수 없습니다.");
        }

        return product;
    }

    @Transactional(readOnly = true)
    public Page<Product> getProducts(Long brandId, ProductSort sort, Pageable pageable) {
        return productRepository.findProducts(brandId, sort, pageable);
    }

    @Transactional(readOnly = true)
    public Product getVisibleProduct(Long id) {
        Product product = getProduct(id);
        if (product.getVisibility() == Product.Visibility.HIDDEN) {
            throw new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 를 찾을 수 없습니다.");
        }

        return product;
    }

    @Transactional(readOnly = true)
    public Page<Product> getAdminProducts(Long brandId, Pageable pageable) {
        return productRepository.findAllProducts(brandId, pageable);
    }

    @Transactional
    public Product update(Long id, ProductUpdateCommand command) {
        Product product = getProduct(id);
        product.update(command.name(), command.description(), command.price(), command.stockQuantity());
        if (command.visibility() != null) {
            product.changeVisibility(command.visibility());
        }

        return product;
    }

    @Transactional
    public void changeVisibility(Long id, Product.Visibility visibility) {
        Product product = getProduct(id);
        product.changeVisibility(visibility);
    }

    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.delete();
    }

    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
        products.forEach(Product::delete);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByIds(List<Long> ids) {
        return productRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                                        "[productId = " + id + "] 를 찾을 수 없습니다."));
    }
}
