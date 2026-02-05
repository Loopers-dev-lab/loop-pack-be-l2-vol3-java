package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member V1 API", description = "회원 API")
public interface MemberV1ApiSpec {

  @Operation(
      summary = "회원 가입 요청",
      description = "주어진 정보를 가지고 회원 가입을 실행한다"
  )
    // @Schema는 Swagger API 문서에서 파라미터 설명을 보여주는 용도
    // 예제에서는 Long exampleId 같은 단일 파라미터에 붙였는데, 지금은 SignUpRequest로 통째로 받으니까 여기엔 필요 없음
  ApiResponse<MemberV1Dto.SignUpResponse> signUp(
      MemberV1Dto.SignUpRequest request
  );

  @Operation(
      summary = "내 정보 조회",
      description = "로그인 ID로 내 회원 정보를 조회한다"
  )
  ApiResponse<MemberV1Dto.MemberInfoResponse> getMyInfo(
          String loginId
  );

}
