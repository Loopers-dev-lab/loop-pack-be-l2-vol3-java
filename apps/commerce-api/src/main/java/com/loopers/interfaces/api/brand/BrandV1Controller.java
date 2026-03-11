package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.CreateBrandCommand;
import com.loopers.application.brand.UpdateBrandCommand;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller {

    private final BrandFacade brandFacade;

    @PostMapping
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(
        @Valid @RequestBody BrandV1Dto.CreateBrandRequest request
    ) {
        CreateBrandCommand command = new CreateBrandCommand(
            request.name(), request.description()
        );
        BrandInfo brandInfo = brandFacade.createBrand(command);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brandInfo));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(
        @PathVariable Long brandId
    ) {
        BrandInfo brandInfo = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brandInfo));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandV1Dto.UpdateBrandRequest request
    ) {
        UpdateBrandCommand command = new UpdateBrandCommand(
            brandId, request.name(), request.description()
        );
        BrandInfo brandInfo = brandFacade.updateBrand(command);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brandInfo));
    }
}
