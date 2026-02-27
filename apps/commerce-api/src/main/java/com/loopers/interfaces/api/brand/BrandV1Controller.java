package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandAppService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 브랜드 고객 API V1 REST 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>활성 상태의 브랜드 목록 조회 및 브랜드 상세 조회 기능을 제공한다.
 * {@link BrandAppService}를 호출한다.</p>
 */
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandV1Controller {

    private final BrandAppService brandAppService;

    /**
     * 활성 브랜드 목록을 조회한다.
     *
     * @param keyword 브랜드명 검색 키워드 (선택)
     * @return 활성 상태의 브랜드 목록 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandV1Dto.BrandResponse>>> list(
            @RequestParam(value = "q", required = false) String keyword) {
        List<BrandInfo> brands = brandAppService.findAllVisibleBrands(keyword);
        List<BrandV1Dto.BrandResponse> response = brands.stream()
                .map(BrandV1Dto.BrandResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 브랜드 상세 정보를 조회한다.
     *
     * @param brandId 조회할 브랜드 ID
     * @return 브랜드 상세 정보 응답
     */
    @GetMapping("/{brandId}")
    public ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> detail(@PathVariable String brandId) {
        BrandInfo info = brandAppService.findVisibleById(brandId);
        return ResponseEntity.ok(ApiResponse.success(BrandV1Dto.BrandResponse.from(info)));
    }
}
