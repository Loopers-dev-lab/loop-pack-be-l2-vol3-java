package com.loopers.interfaces.api.coderabbit;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Code Rabbit V1 API", description = "CodeRabbit 테스트용 API 입니다.")
public interface CodeRabbitV1ApiSpec {

    @Operation(
        summary = "CodeRabbit 핑 테스트",
        description = "CodeRabbit 리뷰 테스트를 위한 ping 응답을 반환합니다."
    )
    ApiResponse<CodeRabbitV1Dto.PingResponse> ping();

    @Operation(
        summary = "CodeRabbit Hello 테스트",
        description = "CodeRabbit 리뷰 테스트를 위한 hello 응답을 반환합니다."
    )
    ApiResponse<CodeRabbitV1Dto.HelloResponse> hello();

    @Operation(
        summary = "CodeRabbit 상태 조회",
        description = "API 상태와 버전 정보를 반환합니다."
    )
    ApiResponse<CodeRabbitV1Dto.StatusResponse> status();
}
