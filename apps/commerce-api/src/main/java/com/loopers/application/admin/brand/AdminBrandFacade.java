package com.loopers.application.admin.brand;

import com.loopers.domain.brand.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminBrandFacade {
    private final AdminBrandAppService adminBrandAppService;

    public Brand create(String name) {
        return adminBrandAppService.create(name);
    }

    public Brand update(Long id, String name) {
        return adminBrandAppService.update(id, name);
    }

    public void delete(Long id) {
        adminBrandAppService.delete(id);
    }

    public Brand getById(Long id) {
        return adminBrandAppService.getById(id);
    }

    public List<Brand> getAll() {
        return adminBrandAppService.getAll();
    }
}
