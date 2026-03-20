package com.loopers.interfaces.api.user;

import com.loopers.domain.user.UserModel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자(User) API V1 요청/응답 DTO 모음.
 *
 * <p>사용자 관련 REST API의 HTTP 요청 및 응답 데이터 구조를 정의한다.
 * Bean Validation을 통한 입력 검증을 포함한다.</p>
 */
public class UserV1Dto {

    /**
     * 회원가입 요청 DTO.
     *
     * <p>로그인 ID, 비밀번호, 이름, 생년월일, 이메일, 주소를 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegisterRequest {
        @NotBlank(message = "로그인 ID는 필수입니다")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 16, message = "비밀번호는 8~16자여야 합니다")
        private String password;

        @NotBlank(message = "이름은 필수입니다")
        private String userName;

        @NotBlank(message = "생년월일은 필수입니다")
        private String birthday;

        @Email(message = "올바른 이메일 형식이 아닙니다")
        private String email;

        private String address;
    }

    /**
     * 회원가입 응답 DTO.
     *
     * <p>생성된 사용자 ID, 로그인 ID, 마스킹된 이름, 생년월일, 이메일, 주소를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class RegisterResponse {
        private Long userId;
        private String loginId;
        private String maskedName;
        private String birthday;
        private String email;
        private String address;

        /**
         * {@link UserModel}을 회원가입 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param model 변환할 사용자 도메인 모델
         * @return 변환된 RegisterResponse
         */
        public static RegisterResponse from(UserModel model) {
            return RegisterResponse.builder()
                    .userId(model.getUserId())
                    .loginId(model.getLoginId())
                    .maskedName(model.getMaskedName())
                    .birthday(model.getBirthday())
                    .email(model.getEmail())
                    .address(model.getAddress())
                    .build();
        }
    }

    /**
     * 내 정보 조회 응답 DTO.
     *
     * <p>사용자 ID, 로그인 ID, 마스킹된 이름, 생년월일, 이메일, 주소를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class MyInfoResponse {
        private Long userId;
        private String loginId;
        private String maskedName;
        private String birthday;
        private String email;
        private String address;

        /**
         * {@link UserModel}을 내 정보 조회 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param model 변환할 사용자 도메인 모델
         * @return 변환된 MyInfoResponse
         */
        public static MyInfoResponse from(UserModel model) {
            return MyInfoResponse.builder()
                    .userId(model.getUserId())
                    .loginId(model.getLoginId())
                    .maskedName(model.getMaskedName())
                    .birthday(model.getBirthday())
                    .email(model.getEmail())
                    .address(model.getAddress())
                    .build();
        }
    }

    /**
     * 비밀번호 변경 요청 DTO.
     *
     * <p>현재 비밀번호와 새 비밀번호를 포함한다.</p>
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
}
