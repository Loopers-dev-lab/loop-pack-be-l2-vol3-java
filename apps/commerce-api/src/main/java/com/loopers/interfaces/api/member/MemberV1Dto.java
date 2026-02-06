package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 회원 API V1 DTO 모음
 *
 * Interface Layer의 DTO:
 * - HTTP 요청/응답 데이터 구조 정의
 * - Bean Validation을 통한 입력 검증
 * - Application Layer의 DTO와 분리
 */
public class MemberV1Dto {

    // ========================================
    // Request DTO
    // ========================================

    /**
     * 회원가입 요청
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegisterRequest {

        @NotBlank(message = "로그인 ID는 필수입니다")
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "로그인 ID는 영문과 숫자만 허용됩니다")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 16, message = "비밀번호는 8~16자여야 합니다")
        private String loginPw;

        @NotBlank(message = "이름은 필수입니다")
        private String name;

        @NotNull(message = "생년월일은 필수입니다")
        @Past(message = "생년월일은 과거 날짜여야 합니다")
        private LocalDate birthDate;

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        private String email;
    }

    /**
     * 비밀번호 변경 요청
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChangePasswordRequest {

        @NotBlank(message = "현재 비밀번호는 필수입니다")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Size(min = 8, max = 16, message = "비밀번호는 8~16자여야 합니다")
        private String newPassword;
    }

    // ========================================
    // Response DTO
    // ========================================

    /**
     * 회원가입 응답
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class RegisterResponse {
        private String loginId;
        private String name;
        private LocalDate birthDate;
        private String email;

        /**
         * MemberInfo를 RegisterResponse로 변환
         */
        public static RegisterResponse from(MemberInfo memberInfo) {
            return RegisterResponse.builder()
                    .loginId(memberInfo.getLoginId())
                    .name(memberInfo.getName())
                    .birthDate(memberInfo.getBirthDate())
                    .email(memberInfo.getEmail())
                    .build();
        }
    }

    /**
     * 내 정보 조회 응답
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class MyInfoResponse {
        private String loginId;
        private String name;  // 마스킹된 이름
        private LocalDate birthDate;
        private String email;

        /**
         * MemberInfo를 MyInfoResponse로 변환
         */
        public static MyInfoResponse from(MemberInfo memberInfo) {
            return MyInfoResponse.builder()
                    .loginId(memberInfo.getLoginId())
                    .name(memberInfo.getMaskedName())  // 마스킹된 이름 사용
                    .birthDate(memberInfo.getBirthDate())
                    .email(memberInfo.getEmail())
                    .build();
        }
    }
}
