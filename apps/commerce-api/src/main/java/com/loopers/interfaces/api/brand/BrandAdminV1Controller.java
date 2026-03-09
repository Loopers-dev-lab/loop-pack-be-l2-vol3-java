package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.BrandCommand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/brands")
@RequiredArgsConstructor
public class BrandAdminV1Controller {

    private final BrandFacade brandFacade;

    @PostMapping
    public ApiResponse<BrandV1Dto.Response> registerBrand(
            @RequestBody BrandV1Dto.RegisterRequest request
    ) {
        BrandCommand command = new BrandCommand(request.name(), request.description(), request.logoUrl());
        BrandInfo brandInfo = brandFacade.registerBrand(command);

        return ApiResponse.success(BrandV1Dto.Response.from(brandInfo));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.Response> getBrand(
            @PathVariable Long brandId
    ) {
        BrandInfo brandInfo = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.Response.from(brandInfo));
    }

    @GetMapping
    public ApiResponse<Page<BrandV1Dto.Response>> getBrands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BrandInfo> brands = brandFacade.getBrands(pageable);
        return ApiResponse.success(brands.map(BrandV1Dto.Response::from));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.Response> updateBrand(
            @PathVariable Long brandId,
            @RequestBody BrandV1Dto.UpdateRequest request
    ) {
        BrandCommand command = new BrandCommand(request.name(), request.description(), request.logoUrl());
        BrandInfo brandInfo = brandFacade.updateBrand(brandId, command);

        return ApiResponse.success(BrandV1Dto.Response.from(brandInfo));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
            @PathVariable Long brandId
    ) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
