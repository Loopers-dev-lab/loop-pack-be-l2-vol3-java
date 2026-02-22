package com.loopers.application.admin.brand;

import com.loopers.application.admin.product.AdminProductAppService;
import com.loopers.domain.brand.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminBrandFacade {
    private final AdminBrandAppService adminBrandAppService;
    private final AdminProductAppService adminProductAppService;

    public Brand create(String name) {
        return adminBrandAppService.create(name);
    }

    public Brand update(Long id, String name) {
        return adminBrandAppService.update(id, name);
    }

    @Transactional
    public void delete(Long id) {
        adminProductAppService.deleteByBrandId(id);
        adminBrandAppService.delete(id);
    }

    public Brand getById(Long id) {
        return adminBrandAppService.getById(id);
    }

    public List<Brand> getAll() {
        return adminBrandAppService.getAll();
    }
}
