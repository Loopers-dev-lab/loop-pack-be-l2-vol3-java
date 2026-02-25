package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandFacade brandFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<BrandAdminV1Dto.BrandResponse>> getAll(Pageable pageable) {
        Page<BrandAdminV1Dto.BrandResponse> response = brandFacade.getAll(pageable)
            .map(BrandAdminV1Dto.BrandResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PostMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> register(
        @Valid @RequestBody BrandAdminV1Dto.RegisterRequest request
    ) {
        BrandInfo info = brandFacade.register(request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> update(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandAdminV1Dto.UpdateRequest request
    ) {
        BrandInfo info = brandFacade.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandFacade.delete(brandId);
        return ApiResponse.success(null);
    }
}
