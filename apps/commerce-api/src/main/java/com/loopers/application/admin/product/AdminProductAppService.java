package com.loopers.application.admin.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
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
public class AdminProductAppService {
    private final ProductRepository productRepository;
    private final OptionRepository optionRepository;

    @Transactional
    public Product create(Long brandId, String name, Money basePrice) {
        Product product = Product.create(brandId, name, basePrice);
        return productRepository.save(product);
    }

    @Transactional
    public Option createOption(Long productId, String name, Money additionalPrice, int stock) {
        getById(productId);
        Option option = Option.create(productId, name, additionalPrice, stock);
        return optionRepository.save(option);
    }

    @Transactional
    public Product update(Long id, String name, Money basePrice) {
        Product product = getById(id);
        product.update(name, basePrice);
        return product;
    }

    @Transactional
    public void delete(Long id) {
        Product product = getById(id);
        product.delete();
        deleteOptionsByProductId(id);
    }

    @Transactional
    public void deleteByBrandId(Long brandId) {
        List<Product> products = productRepository.findByBrandId(brandId);
        for (Product product : products) {
            product.delete();
            deleteOptionsByProductId(product.getId());
        }
    }

    @Transactional
    public void deleteOptionsByProductId(Long productId) {
        List<Option> options = optionRepository.findByProductId(productId);
        for (Option option : options) {
            option.delete();
        }
    }

    @Transactional
    public Option updateOptionStock(Long productId, Long optionId, int stock) {
        getById(productId);
        Option option = getOptionById(optionId);
        if (!option.getProductId().equals(productId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "해당 상품의 옵션이 아닙니다.");
        }
        option.updateStock(stock);
        return option;
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Option getOptionById(Long id) {
        return optionRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "옵션을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Product> getAll() {
        return productRepository.findAll(null);
    }

    @Transactional(readOnly = true)
    public List<Option> getOptionsByProductId(Long productId) {
        return optionRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public ProductWithOptions getProductWithOptions(Long id) {
        Product product = getById(id);
        List<Option> options = getOptionsByProductId(id);
        return new ProductWithOptions(product, options);
    }

    public record ProductWithOptions(Product product, List<Option> options) {}
}
