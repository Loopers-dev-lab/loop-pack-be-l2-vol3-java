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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductAppService {
    private static final int CACHEABLE_PAGE_LIMIT = 2;

    private final ProductRepository productRepository;
    private final OptionRepository optionRepository;
    private final ProductCacheManager productCacheManager;

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
    public Page<Product> getProductsByBrandId(Long brandId, int page, int size) {
        return productRepository.findByBrandIdWithPaging(brandId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public CachedProductDetail getProductDetailCached(Long productId) {
        Optional<CachedProductDetail> cached = productCacheManager.getProductDetail(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Product product = getById(productId);
        List<Option> options = optionRepository.findByProductId(productId);
        long likeCount = product.getLikeCount();

        CachedProductDetail detail = CachedProductDetail.from(product, options, likeCount);
        productCacheManager.putProductDetail(productId, detail);
        return detail;
    }

    @Transactional(readOnly = true)
    public CachedBrandProductPage getProductsByBrandIdCached(Long brandId, int page, int size) {
        if (page <= CACHEABLE_PAGE_LIMIT) {
            Optional<CachedBrandProductPage> cached = productCacheManager.getProductList(brandId, page, size);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        Page<Product> products = productRepository.findByBrandIdWithPaging(brandId, PageRequest.of(page, size));
        CachedBrandProductPage result = CachedBrandProductPage.builder()
                .content(products.getContent().stream().map(CachedBrandProductPage.ProductSummary::from).toList())
                .totalElements(products.getTotalElements())
                .build();

        if (page <= CACHEABLE_PAGE_LIMIT) {
            productCacheManager.putProductList(brandId, page, size, result);
        }

        return result;
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
    public Option getOptionByIdWithLock(Long optionId) {
        return optionRepository.findByIdWithLock(optionId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "옵션을 찾을 수 없습니다."));
    }

    @Transactional
    public Option decreaseStock(Long optionId, int quantity) {
        Option option = optionRepository.findByIdWithLock(optionId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "옵션을 찾을 수 없습니다."));
        option.decreaseStock(quantity);
        return option;
    }

    @Transactional
    public Option increaseStock(Long optionId, int quantity) {
        Option option = optionRepository.findByIdWithLock(optionId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "옵션을 찾을 수 없습니다."));
        option.increaseStock(quantity);
        return option;
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
