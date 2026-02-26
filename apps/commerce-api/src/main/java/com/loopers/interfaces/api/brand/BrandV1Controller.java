package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandV1Controller implements BrandApiV1Spec {

    private final BrandFacade brandFacade;

    // Query

    @GetMapping
    @Override
    public ApiResponse<PageResponse<BrandV1Dto.BrandResponse>> list(
            BrandRequest.ListActive request) {
        Page<BrandInfo> brands = brandFacade.getActiveList(request);
        PageResponse<BrandV1Dto.BrandResponse> pageResponse =
                PageResponse.from(brands, BrandV1Dto.BrandResponse::from);
        return ApiResponse.success(pageResponse);
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> detail(@PathVariable Long brandId) {
        BrandInfo info = brandFacade.getActiveDetail(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }
}
