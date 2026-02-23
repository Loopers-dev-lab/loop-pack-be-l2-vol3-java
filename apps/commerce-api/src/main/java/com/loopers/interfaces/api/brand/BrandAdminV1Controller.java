package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandAdminFacade brandAdminFacade;

    @PostMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> registerBrand(
            @RequestBody BrandAdminV1Dto.RegisterRequest request) {
        BrandInfo info = brandAdminFacade.register(request.name());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @GetMapping("/{id}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrandDetails(
            @PathVariable long id) {
        BrandInfo info = brandAdminFacade.findById(id);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandListResponse> getBrandList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<BrandInfo> infoPage = brandAdminFacade.findAll(PageRequest.of(page, size));
        return ApiResponse.success(BrandAdminV1Dto.BrandListResponse.from(infoPage));
    }

    @PutMapping("/{id}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> updateBrand(
            @PathVariable long id,
            @RequestBody BrandAdminV1Dto.UpdateRequest request) {
        BrandInfo info = brandAdminFacade.update(request.toCommand(id));
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{id}")
    @Override
    public ApiResponse<Void> deleteBrand(@PathVariable long id) {
        brandAdminFacade.delete(id);
        return ApiResponse.success(null);
    }
}
