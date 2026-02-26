package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandQueryUseCase;
import com.loopers.interfaces.api.brand.dto.BrandResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/brands")
public class BrandController {

    private final BrandQueryUseCase brandQueryUseCase;

    public BrandController(BrandQueryUseCase brandQueryUseCase) {
        this.brandQueryUseCase = brandQueryUseCase;
    }

    @GetMapping("/{brandId}")
    public ResponseEntity<BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandQueryUseCase.BrandInfo brandInfo = brandQueryUseCase.getBrand(brandId);
        return ResponseEntity.ok(BrandResponse.from(brandInfo));
    }
}
