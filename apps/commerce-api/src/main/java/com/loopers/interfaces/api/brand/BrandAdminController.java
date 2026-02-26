package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandQueryUseCase;
import com.loopers.application.brand.CreateBrandUseCase;
import com.loopers.application.brand.DeleteBrandUseCase;
import com.loopers.application.brand.UpdateBrandUseCase;
import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.brand.dto.BrandResponse;
import com.loopers.interfaces.api.brand.dto.BrandUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminController {

    private final CreateBrandUseCase createBrandUseCase;
    private final UpdateBrandUseCase updateBrandUseCase;
    private final DeleteBrandUseCase deleteBrandUseCase;
    private final BrandQueryUseCase brandQueryUseCase;

    public BrandAdminController(CreateBrandUseCase createBrandUseCase,
                                UpdateBrandUseCase updateBrandUseCase,
                                DeleteBrandUseCase deleteBrandUseCase,
                                BrandQueryUseCase brandQueryUseCase) {
        this.createBrandUseCase = createBrandUseCase;
        this.updateBrandUseCase = updateBrandUseCase;
        this.deleteBrandUseCase = deleteBrandUseCase;
        this.brandQueryUseCase = brandQueryUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> createBrand(@RequestBody BrandCreateRequest request) {
        createBrandUseCase.createBrand(request.name(), request.description());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{brandId}")
    public ResponseEntity<Void> updateBrand(@PathVariable Long brandId,
                                            @RequestBody BrandUpdateRequest request) {
        updateBrandUseCase.updateBrand(brandId, request.name(), request.description());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{brandId}")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long brandId) {
        deleteBrandUseCase.deleteBrand(brandId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<BrandResponse>> getBrands() {
        List<BrandResponse> brands = brandQueryUseCase.getBrands().stream()
                .map(BrandResponse::from)
                .toList();
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/{brandId}")
    public ResponseEntity<BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandQueryUseCase.BrandInfo brandInfo = brandQueryUseCase.getBrand(brandId);
        return ResponseEntity.ok(BrandResponse.from(brandInfo));
    }
}
