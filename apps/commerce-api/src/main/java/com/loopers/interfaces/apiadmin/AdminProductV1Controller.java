package com.loopers.interfaces.apiadmin;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductUpdateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductRevisionModel;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 전용 상품 REST API 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>상품의 전체 조회, 등록, 수정, 삭제 및 변경 이력(revision) 조회 기능을 관리자에게 제공한다.
 * 복잡한 도메인으로 {@link ProductFacade}를 통해 여러 서비스를 조합하여 처리한다.
 * 단순 삭제 및 이력 조회는 {@link ProductService}를 직접 호출한다.</p>
 */
@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class AdminProductV1Controller {

    private final ProductFacade productFacade;
    private final ProductService productService;

    /**
     * 전체 상품 목록을 조회한다.
     *
     * @param includeDeleted 삭제된 상품 포함 여부 (기본값: false)
     * @return 상품 목록 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminProductV1Dto.AdminProductResponse>>> list(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        List<ProductInfo> products = productFacade.getProductsForAdmin(includeDeleted);
        List<AdminProductV1Dto.AdminProductResponse> response = products.stream()
                .map(AdminProductV1Dto.AdminProductResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 새로운 상품을 등록한다.
     *
     * @param request 상품 생성 요청 DTO
     * @return 생성된 상품 정보 응답
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminProductV1Dto.AdminProductResponse>> create(
            @Valid @RequestBody AdminProductV1Dto.CreateProductRequest request) {
        ProductInfo info = productFacade.createProduct(new ProductCreateCommand(
                request.getProductName(), request.getBrandId(),
                request.getPrice(), request.getDescription(), request.getInitialStock()));
        return ResponseEntity.ok(ApiResponse.success(AdminProductV1Dto.AdminProductResponse.from(info)));
    }

    /**
     * 기존 상품 정보를 수정한다.
     *
     * @param productId 수정할 상품 ID
     * @param request   상품 수정 요청 DTO
     * @return 수정된 상품 정보 응답
     */
    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<AdminProductV1Dto.AdminProductResponse>> update(
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductV1Dto.UpdateProductRequest request) {
        ProductInfo info = productFacade.updateProduct(new ProductUpdateCommand(
                productId, request.getProductName(), request.getPrice(),
                request.getDescription(), request.getImageUrl()));
        return ResponseEntity.ok(ApiResponse.success(AdminProductV1Dto.AdminProductResponse.from(info)));
    }

    /**
     * 상품을 소프트 삭제한다.
     *
     * @param productId 삭제할 상품 ID
     * @return 삭제 성공 응답
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 특정 상품의 변경 이력(revision) 목록을 조회한다.
     *
     * @param productId 조회할 상품 ID
     * @return 상품 변경 이력 목록 응답
     */
    @GetMapping("/{productId}/revisions")
    public ResponseEntity<ApiResponse<List<AdminProductV1Dto.RevisionResponse>>> getRevisions(
            @PathVariable Long productId) {
        List<ProductRevisionModel> revisions = productService.findRevisionsByProductId(productId);
        List<AdminProductV1Dto.RevisionResponse> response = revisions.stream()
                .map(AdminProductV1Dto.RevisionResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 특정 상품의 개별 변경 이력 상세 정보를 조회한다.
     *
     * @param productId 조회할 상품 ID
     * @param seq       조회할 리비전 시퀀스 번호
     * @return 상품 변경 이력 상세 응답
     */
    @GetMapping("/{productId}/revisions/{seq}")
    public ResponseEntity<ApiResponse<AdminProductV1Dto.RevisionResponse>> getRevisionDetail(
            @PathVariable Long productId, @PathVariable Long seq) {
        ProductRevisionModel revision = productService.findRevisionById(productId, seq);
        return ResponseEntity.ok(ApiResponse.success(AdminProductV1Dto.RevisionResponse.from(revision)));
    }
}
