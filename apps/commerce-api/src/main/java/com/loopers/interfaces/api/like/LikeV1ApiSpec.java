package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Like V1 API", description = "좋아요 API")
@RequestMapping("/api/v1/likes")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "상품에 좋아요를 등록합니다.")
    ApiResponse<Void> like(@Valid @RequestBody LikeV1Dto.LikeRequest request);

    @Operation(summary = "좋아요 취소", description = "상품 좋아요를 취소합니다.")
    ApiResponse<Void> unlike(
        @RequestParam Long memberId,
        @RequestParam Long productId
    );
}
