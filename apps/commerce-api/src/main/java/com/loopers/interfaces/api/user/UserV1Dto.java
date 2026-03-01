package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserDto;
import com.loopers.domain.user.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * User V1 API DTO
 *
 * Interface Layer의 HTTP API 스펙을 정의합니다.
 * - Application Layer의 UserDto로부터 생성
 * - API 버전별로 독립적인 스펙 유지
 */
public class UserV1Dto {

    public record CreateRequest(
        @NotBlank(message = "이름은 필수입니다")
        String name,

        @NotNull(message = "생년월일은 필수입니다")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 올바르지 않습니다")
        String email,

        @NotNull(message = "성별은 필수입니다")
        Gender gender
    ) {
    }

    public record CreateResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender
    ) {
        /**
         * Application DTO(UserInfo)로부터 API Response를 생성합니다.
         *
         * @param userInfo Application Layer의 UserInfo DTO
         * @return API Response DTO
         */
        public static CreateResponse from(UserDto.UserInfo userInfo) {
            return new CreateResponse(
                userInfo.id(),
                userInfo.loginId(),
                userInfo.name(),
                userInfo.birthDate(),
                userInfo.email(),
                userInfo.gender()
            );
        }
    }

    public record UserInfoResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender
    ) {
        /**
         * Application DTO(UserInfo)로부터 API Response를 생성합니다.
         *
         * @param userInfo Application Layer의 UserInfo DTO
         * @return API Response DTO
         */
        public static UserInfoResponse from(UserDto.UserInfo userInfo) {
            return new UserInfoResponse(
                userInfo.id(),
                userInfo.loginId(),
                userInfo.name(),
                userInfo.birthDate(),
                userInfo.email(),
                userInfo.gender()
            );
        }
    }

    public record ChangePasswordRequest(
        @NotBlank(message = "기존 비밀번호는 필수입니다")
        String oldPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다")
        String newPassword
    ) {
    }
}
