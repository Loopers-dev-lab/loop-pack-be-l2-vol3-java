package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Product API", description = "상품 관리 API")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 단건 조회", description = "productId로 상품 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getProduct(
            @Parameter(description = "상품 ID", example = "prod1")
            @PathVariable String productId,
            @Parameter(description = "유저 ID (View 이벤트 중복 방지용, 익명은 생략)", example = "42")
            @org.springframework.web.bind.annotation.RequestHeader(name = "X-USER-ID", required = false) Long memberId
    );

    @Operation(summary = "상품 수정", description = "상품 정보(상품명, 가격, 재고)를 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> updateProduct(
            @Parameter(description = "상품 ID", example = "prod1")
            @PathVariable String productId,
            @RequestBody ProductV1Dto.UpdateProductRequest request
    );

    @Operation(summary = "상품 생성", description = "새로운 상품을 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "상품 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "브랜드를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복된 상품 ID")
    })
    ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "상품 생성 요청",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ProductV1Dto.CreateProductRequest.class))
            )
            @RequestBody ProductV1Dto.CreateProductRequest request
    );

    @Operation(summary = "상품 목록 조회", description = "상품 목록을 조회합니다. 브랜드 필터링, 정렬, 페이징을 지원합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> getProducts(
            @Parameter(description = "브랜드 ID (선택)", example = "nike")
            @RequestParam(required = false) String brandId,

            @Parameter(description = "정렬 기준 (latest: 최신순, price_asc: 가격 낮은순)", example = "latest")
            @RequestParam(required = false, defaultValue = "latest") String sort,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(summary = "상품 목록 조회 (커서 기반)", description = "커서 기반 페이징으로 상품 목록을 조회합니다. 데이터 삽입/삭제 시에도 Skip/Duplicate가 발생하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 커서")
    })
    ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> getProductsByCursor(
            @Parameter(description = "브랜드 ID (선택)", example = "nike")
            @RequestParam(required = false) String brandId,

            @Parameter(description = "정렬 기준 (latest: 최신순, likes_desc: 좋아요순, price_asc: 가격 낮은순)", example = "latest")
            @RequestParam(required = false, defaultValue = "latest") String sort,

            @Parameter(description = "커서 (첫 페이지는 생략, 이후 응답의 nextCursor 값을 사용)")
            @RequestParam(required = false) String cursor,

            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다 (Soft Delete).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "상품 ID", example = "prod1")
            @PathVariable String productId
    );
}
