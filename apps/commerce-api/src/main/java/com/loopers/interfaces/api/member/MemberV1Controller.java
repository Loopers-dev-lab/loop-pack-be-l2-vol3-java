package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 회원 API V1 Controller
 *
 * Interface Layer의 역할:
 * - HTTP 요청/응답 처리
 * - 입력 검증 (Bean Validation)
 * - 인증 헤더 파싱
 * - DTO 변환
 * - HTTP 상태 코드 반환
 *
 * 응답 형식:
 * - ApiResponse<T> 사용 (일관된 응답 형식)
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberV1Controller {

    private final MemberFacade memberFacade;

    // ========================================
    // 1. 회원가입
    // ========================================

    /**
     * 회원가입
     *
     * POST /api/v1/members
     *
     * @param request 회원가입 요청 DTO
     * @return 생성된 회원 정보 (ApiResponse 형식)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MemberV1Dto.RegisterResponse>> register(
            @Valid @RequestBody MemberV1Dto.RegisterRequest request
    ) {
        // 1. Facade 호출
        MemberInfo memberInfo = memberFacade.register(
                request.getLoginId(),
                request.getLoginPw(),
                request.getName(),
                request.getBirthDate(),
                request.getEmail()
        );

        // 2. Response DTO 변환
        MemberV1Dto.RegisterResponse response = MemberV1Dto.RegisterResponse.from(memberInfo);

        // 3. ApiResponse로 감싸서 200 OK 반환
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========================================
    // 2. 내 정보 조회
    // ========================================

    /**
     * 내 정보 조회 (인증 필요)
     *
     * GET /api/v1/members/me
     *
     * Headers:
     * - X-Loopers-LoginId: 로그인 ID
     * - X-Loopers-LoginPw: 비밀번호
     *
     * @param loginId 로그인 ID (헤더)
     * @param loginPw 비밀번호 (헤더)
     * @return 마스킹된 회원 정보 (ApiResponse 형식)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberV1Dto.MyInfoResponse>> getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        // 1. Facade 호출 (인증 + 조회)
        MemberInfo memberInfo = memberFacade.getMyInfo(loginId, loginPw);

        // 2. Response DTO 변환
        MemberV1Dto.MyInfoResponse response = MemberV1Dto.MyInfoResponse.from(memberInfo);

        // 3. ApiResponse로 감싸서 200 OK 반환
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========================================
    // 3. 비밀번호 변경
    // ========================================

    /**
     * 비밀번호 변경 (인증 필요)
     *
     * PATCH /api/v1/members/me/password
     *
     * Headers:
     * - X-Loopers-LoginId: 로그인 ID
     * - X-Loopers-LoginPw: 비밀번호 (인증용)
     *
     * Body:
     * - currentPassword: 현재 비밀번호 (재확인용)
     * - newPassword: 새 비밀번호
     *
     * @param loginId 로그인 ID (헤더)
     * @param loginPw 비밀번호 (헤더)
     * @param request 비밀번호 변경 요청 DTO
     * @return 성공 응답 (ApiResponse 형식)
     */
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Object>> changePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @Valid @RequestBody MemberV1Dto.ChangePasswordRequest request
    ) {
        // 1. Facade 호출 (인증 + 변경)
        memberFacade.changePassword(
                loginId,
                loginPw,
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        // 2. ApiResponse로 감싸서 200 OK 반환 (데이터 없음)
        return ResponseEntity.ok(ApiResponse.success());
    }
}
