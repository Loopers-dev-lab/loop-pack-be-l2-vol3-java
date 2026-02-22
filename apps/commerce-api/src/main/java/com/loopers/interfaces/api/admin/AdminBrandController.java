package com.loopers.interfaces.api.admin;

import com.loopers.application.admin.brand.AdminBrandFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {
    private final AdminBrandFacade adminBrandFacade;

    @PostMapping
    public ApiResponse<AdminBrandDto.Response> create(
            @LoginAdmin String adminId,
            @RequestBody AdminBrandDto.CreateRequest request) {
        Brand brand = adminBrandFacade.create(request.name());
        return ApiResponse.success(AdminBrandDto.Response.from(brand));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminBrandDto.Response> update(
            @LoginAdmin String adminId,
            @PathVariable Long id,
            @RequestBody AdminBrandDto.UpdateRequest request) {
        Brand brand = adminBrandFacade.update(id, request.name());
        return ApiResponse.success(AdminBrandDto.Response.from(brand));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @LoginAdmin String adminId,
            @PathVariable Long id) {
        adminBrandFacade.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<AdminBrandDto.ListResponse> getAll(@LoginAdmin String adminId) {
        List<Brand> brands = adminBrandFacade.getAll();
        return ApiResponse.success(AdminBrandDto.ListResponse.from(brands));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminBrandDto.Response> getById(
            @LoginAdmin String adminId,
            @PathVariable Long id) {
        Brand brand = adminBrandFacade.getById(id);
        return ApiResponse.success(AdminBrandDto.Response.from(brand));
    }
}
