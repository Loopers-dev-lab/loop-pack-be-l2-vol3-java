package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_IMAGE_URL_LENGTH = 500;

    private final ProductRepository productRepository;

    @Transactional
    public Product register(Long brandId, String name, String description, BigDecimal price, Integer stock, String imageUrl) {
        validateNameLength(name);
        validateDescriptionLength(description);
        validateImageUrlLength(imageUrl);

        Product product = Product.create(brandId, name, description, price, stock, imageUrl);
        return productRepository.save(product);
    }

    public Product getById(Long id) {
        return productRepository.findActiveById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    public Page<Product> getAll(Pageable pageable) {
        return productRepository.findAllActive(pageable);
    }

    public Page<Product> getAllByBrandId(Long brandId, Pageable pageable) {
        return productRepository.findAllActiveByBrandId(brandId, pageable);
    }

    public List<Product> getByIds(List<Long> ids) {
        return productRepository.findAllActiveByIdIn(ids);
    }

    public List<Long> getIdsByBrandId(Long brandId) {
        return productRepository.findAllActiveIdsByBrandId(brandId);
    }

    @Transactional
    public Product update(Long id, String name, String description, BigDecimal price, Integer stock, String imageUrl) {
        Product product = productRepository.findActiveById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (name != null) {
            validateNameLength(name);
        }
        if (description != null) {
            validateDescriptionLength(description);
        }
        if (imageUrl != null) {
            validateImageUrlLength(imageUrl);
        }

        product.update(name, description, price, stock, imageUrl);
        return product;
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findActiveById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        product.delete();
    }

    @Transactional
    public Product decreaseStock(Long productId, Integer quantity) {
        Product product = getById(productId);
        product.decreaseStock(quantity);
        return product;
    }

    @Transactional
    public void increaseLikes(Long productId) {
        Product product = getById(productId);
        product.increaseLikes();
    }

    @Transactional
    public void decreaseLikes(Long productId) {
        Product product = getById(productId);
        product.decreaseLikes();
    }

    @Transactional
    public void deleteByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllActiveByBrandId(brandId);
        products.forEach(Product::delete);
    }

    private void validateNameLength(String name) {
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 200자를 초과할 수 없습니다.");
        }
    }

    private void validateDescriptionLength(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "설명은 2000자를 초과할 수 없습니다.");
        }
    }

    private void validateImageUrlLength(String imageUrl) {
        if (imageUrl != null && imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미지 URL은 500자를 초과할 수 없습니다.");
        }
    }
}
