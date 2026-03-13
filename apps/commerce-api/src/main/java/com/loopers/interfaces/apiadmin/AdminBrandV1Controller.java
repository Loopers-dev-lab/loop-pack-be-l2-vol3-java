package com.loopers.interfaces.apiadmin;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 전용 브랜드 REST API 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>브랜드의 전체 조회, 등록, 수정, 삭제 기능을 관리자에게 제공한다.
 * 브랜드 삭제 시 소속 상품도 연쇄적으로 소프트 삭제된다.
 * {@link BrandService}를 직접 호출한다.</p>
 */
@RestController
@RequestMapping("/api-admin/v1/brands")
@RequiredArgsConstructor
public class AdminBrandV1Controller {

    private final BrandService brandService;

    /**
     * 전체 브랜드 목록을 조회한다 (삭제된 브랜드 포함).
     *
     * @return 전체 브랜드 목록 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminBrandV1Dto.AdminBrandResponse>>> list() {
        List<AdminBrandV1Dto.AdminBrandResponse> response = brandService.findAllForAdmin().stream()
                .map(AdminBrandV1Dto.AdminBrandResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 새로운 브랜드를 등록한다.
     *
     * @param request 브랜드 생성 요청 DTO
     * @return 생성된 브랜드 정보 응답
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminBrandV1Dto.AdminBrandResponse>> create(
            @Valid @RequestBody AdminBrandV1Dto.CreateBrandRequest request) {
        BrandModel brand = brandService.createBrand(
                request.getBrandName(), request.getDescription(), request.getAddress());
        return ResponseEntity.ok(ApiResponse.success(AdminBrandV1Dto.AdminBrandResponse.from(brand)));
    }

    /**
     * 기존 브랜드 정보를 수정한다.
     *
     * @param brandId 수정할 브랜드 ID
     * @param request 브랜드 수정 요청 DTO
     * @return 수정된 브랜드 정보 응답
     */
    @PutMapping("/{brandId}")
    public ResponseEntity<ApiResponse<AdminBrandV1Dto.AdminBrandResponse>> update(
            @PathVariable Long brandId,
            @Valid @RequestBody AdminBrandV1Dto.UpdateBrandRequest request) {
        BrandModel brand = brandService.updateBrand(brandId,
                request.getBrandName(), request.getDescription(), request.getAddress());
        return ResponseEntity.ok(ApiResponse.success(AdminBrandV1Dto.AdminBrandResponse.from(brand)));
    }

    /**
     * 브랜드를 소프트 삭제하고, 해당 브랜드 소속 상품도 연쇄적으로 소프트 삭제한다.
     *
     * @param brandId 삭제할 브랜드 ID
     * @return 삭제 성공 응답
     */
    @DeleteMapping("/{brandId}")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable Long brandId) {
        brandService.deleteBrand(brandId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
