package com.loopers.application.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortCondition;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductAppService {
    private final ProductRepository productRepository;
    private final OptionRepository optionRepository;

    @Transactional
    public Product create(Long brandId, String name, Money basePrice) {
        Product product = Product.create(brandId, name, basePrice);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Product> getProducts(ProductSortCondition condition) {
        return productRepository.findAll(condition);
    }

    @Transactional(readOnly = true)
    public Option getOptionById(Long optionId) {
        return optionRepository.findById(optionId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "옵션을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Option> getOptionsByProductId(Long productId) {
        return optionRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Option>> getOptionsByProductIds(List<Long> productIds) {
        List<Option> options = optionRepository.findByProductIdIn(productIds);
        Map<Long, List<Option>> optionMap = new HashMap<>();
        for (Option option : options) {
            optionMap.computeIfAbsent(option.getProductId(), k -> new ArrayList<>()).add(option);
        }
        return optionMap;
    }

    @Transactional(readOnly = true)
    public Map<Long, Option> getOptionsByIds(List<Long> optionIds) {
        List<Option> options = optionRepository.findByIdIn(optionIds);
        Map<Long, Option> optionMap = new HashMap<>();
        for (Option option : options) {
            optionMap.put(option.getId(), option);
        }
        return optionMap;
    }

    @Transactional(readOnly = true)
    public Map<Long, Product> getByIds(List<Long> productIds) {
        List<Product> products = productRepository.findByIdIn(productIds);
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }
        return productMap;
    }

    @Transactional
    public Option createOption(Long productId, String name, Money additionalPrice, int stock) {
        getById(productId);
        Option option = Option.create(productId, name, additionalPrice, stock);
        return optionRepository.save(option);
    }

    @Transactional
    public Option decreaseStock(Long optionId, int quantity) {
        Option option = getOptionById(optionId);
        option.decreaseStock(quantity);
        return optionRepository.save(option);
    }

    @Transactional
    public Option increaseStock(Long optionId, int quantity) {
        Option option = getOptionById(optionId);
        option.increaseStock(quantity);
        return optionRepository.save(option);
    }
}
