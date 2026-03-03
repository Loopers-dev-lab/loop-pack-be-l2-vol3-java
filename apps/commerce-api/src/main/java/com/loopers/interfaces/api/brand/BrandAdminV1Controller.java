package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    // Command

    @PostMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> register(
            @RequestBody BrandRequest.Register request) {
        BrandInfo info = brandFacade.register(request);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PatchMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> updateInfo(
            @PathVariable Long brandId,
            @RequestBody BrandRequest.UpdateInfo request) {
        BrandInfo info = brandFacade.updateInfo(brandId, request);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandFacade.delete(brandId);
        return ApiResponse.success();
    }

    // Query

    @GetMapping
    @Override
    public ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> list(
            BrandRequest.ListAll request) {
        Page<BrandInfo> brands = brandFacade.getList(request);
        PageResponse<BrandAdminV1Dto.BrandResponse> pageResponse =
                PageResponse.from(brands, BrandAdminV1Dto.BrandResponse::from);
        return ApiResponse.success(pageResponse);
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> detail(@PathVariable Long brandId) {
        BrandInfo info = brandFacade.getDetail(brandId);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }
}
