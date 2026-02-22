package com.loopers.application.admin.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminProductFacade {
    private final AdminProductAppService adminProductAppService;

    public Product create(Long brandId, String name, Money basePrice) {
        return adminProductAppService.create(brandId, name, basePrice);
    }

    public Option createOption(Long productId, String name, Money additionalPrice, int stock) {
        return adminProductAppService.createOption(productId, name, additionalPrice, stock);
    }

    public Product update(Long id, String name, Money basePrice) {
        return adminProductAppService.update(id, name, basePrice);
    }

    public void delete(Long id) {
        adminProductAppService.delete(id);
    }

    public Option updateOptionStock(Long productId, Long optionId, int stock) {
        return adminProductAppService.updateOptionStock(productId, optionId, stock);
    }

    public Product getById(Long id) {
        return adminProductAppService.getById(id);
    }

    public List<Product> getAll() {
        return adminProductAppService.getAll();
    }

    public List<Option> getOptionsByProductId(Long productId) {
        return adminProductAppService.getOptionsByProductId(productId);
    }
}
