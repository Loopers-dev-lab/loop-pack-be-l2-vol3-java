package com.loopers.interfaces.api.admin;

import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandV1Controller implements AdminBrandV1ApiSpec {

    private final BrandFacade brandFacade;

    public AdminBrandV1Controller(BrandFacade brandFacade) {
        this.brandFacade = brandFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> createBrand(
        @Valid @RequestBody AdminBrandV1Dto.CreateBrandRequest request
    ) {
        var info = brandFacade.register(request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(new AdminBrandV1Dto.BrandResponse(info.id(), info.name())));
    }

    @GetMapping("/{brandId}")
    @Override
    public ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> getBrand(
        @PathVariable Long brandId
    ) {
        var info = brandFacade.findByIdAndNotDeleted(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: " + brandId));
        return ResponseEntity.ok(ApiResponse.success(new AdminBrandV1Dto.BrandResponse(info.id(), info.name())));
    }

    @PutMapping("/{brandId}")
    @Override
    public ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody AdminBrandV1Dto.UpdateBrandRequest request
    ) {
        var info = brandFacade.update(brandId, request.name());
        return ResponseEntity.ok(ApiResponse.success(new AdminBrandV1Dto.BrandResponse(info.id(), info.name())));
    }

    @DeleteMapping("/{brandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public ResponseEntity<ApiResponse<Void>> deleteBrand(
        @PathVariable Long brandId
    ) {
        brandFacade.delete(brandId);
        return ResponseEntity.noContent().build();
    }
}
