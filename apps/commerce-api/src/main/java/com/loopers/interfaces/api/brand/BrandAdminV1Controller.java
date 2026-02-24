package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/brands")
@RequiredArgsConstructor
public class BrandAdminV1Controller implements BrandAdminApiV1Spec {

    private final BrandFacade brandFacade;

    @PostMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> register(
            @Valid @RequestBody BrandAdminV1Dto.RegisterRequest request) {
        BrandInfo info = brandFacade.register(request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PatchMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> update(
            @PathVariable Long brandId,
            @Valid @RequestBody BrandAdminV1Dto.UpdateRequest request) {
        BrandInfo info = brandFacade.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandFacade.delete(brandId);
        return ApiResponse.success();
    }
}
