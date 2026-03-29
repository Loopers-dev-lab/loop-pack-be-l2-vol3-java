package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "대기열 API", description = "대기열 진입 및 순번 조회 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "대기열에 진입합니다. 이미 대기 중이면 기존 순번을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대기열 진입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "대기열이 가득 참")
    })
    ResponseEntity<ApiResponse<EnterResponse>> enterQueue(@RequestBody EnterRequest request);

    @Operation(summary = "순번 조회", description = "현재 대기 순번과 토큰 발급 여부를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "순번 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대기열에 없음")
    })
    ResponseEntity<ApiResponse<PositionResponse>> getPosition(
            @Parameter(description = "회원 DB PK") @RequestParam Long memberId
    );
}
