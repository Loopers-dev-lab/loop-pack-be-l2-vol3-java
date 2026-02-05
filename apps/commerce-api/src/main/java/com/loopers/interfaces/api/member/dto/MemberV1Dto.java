package com.loopers.interfaces.api.member.dto;

import com.loopers.application.member.MemberInfo;
import com.loopers.application.member.MyInfo;
import com.loopers.domain.member.SignupCommand;

public class MemberV1Dto {

    public record SignupRequest(
        String loginId,
        String password,
        String name,
        String email,
        String birthDate
    ) {
        public SignupCommand toCommand() {
            return new SignupCommand(loginId, password, name, email, birthDate);
        }
    }

    public record SignupResponse(Long memberId) {
        public static SignupResponse from(MemberInfo info) {
            return new SignupResponse(info.id());
        }
    }

    public record MyInfoResponse(
        String loginId,
        String name,
        String email,
        String birthDate
    ) {
        public static MyInfoResponse from(MyInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                info.name(),
                info.email(),
                info.birthDate()
            );
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}
}
