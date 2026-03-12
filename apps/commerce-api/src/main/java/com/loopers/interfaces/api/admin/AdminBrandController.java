package com.loopers.interfaces.api.admin;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.BrandDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandController {

    private final BrandApplicationService brandApplicationService;
    private final BrandAdminFacade brandAdminFacade;

    @GetMapping
    public ApiResponse<BrandDto.BrandListResponse> listBrands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Brand> brands = brandApplicationService.list(pageable);
        return ApiResponse.success(BrandDto.BrandListResponse.from(brands));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandDto.BrandResponse> createBrand(
            @Valid @RequestBody BrandDto.CreateBrandRequest request
    ) {
        Brand brand = brandApplicationService.create(request.toCommand());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable UUID brandId) {
        Brand brand = brandApplicationService.findById(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> updateBrand(
            @PathVariable UUID brandId,
            @Valid @RequestBody BrandDto.UpdateBrandRequest request
    ) {
        Brand brand = brandApplicationService.update(brandId, request.toCommand());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable UUID brandId) {
        brandAdminFacade.delete(brandId);
        return ApiResponse.success();
    }
}
