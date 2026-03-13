package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class MemberV1Dto {

    public record SignUpRequest(
        @NotBlank(message = "로그인ID는 필수입니다")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다")
        String password,

        @NotBlank(message = "이름은 필수입니다")
        String name,

        @NotBlank(message = "생년월일은 필수입니다")
        String birthDate,

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        String email
    ) {}

    public record SignUpResponse(Long id, String loginId, String name, String birthDate, String email) {
        public static SignUpResponse from(Member member) {
            return new SignUpResponse(
                member.getId(),
                member.getLoginId(),
                member.getName(),
                member.getBirthDate(),
                member.getEmail()
            );
        }
    }
}
