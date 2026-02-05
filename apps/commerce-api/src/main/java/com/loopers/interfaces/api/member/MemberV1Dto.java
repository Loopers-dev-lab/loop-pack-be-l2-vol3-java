package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;

public class MemberV1Dto {

    public record SignupRequest(
            String memberId,
            String password,
            String name,
            String email,
            String birthDate
    ) {
        public MemberService.SignupCommand toCommand() {
            return new MemberService.SignupCommand(
                    memberId,
                    password,
                    name,
                    email,
                    birthDate
            );
        }
    }

    public record SignupResponse(
            String memberId,
            String name,
            String email
    ) {
        public static SignupResponse from(Member member) {
            return new SignupResponse(
                    member.getMemberId().getValue(),
                    member.getName().getValue(),
                    member.getEmail().getValue()
            );
        }
    }

    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword
    ) {}

    public record MeResponse(
            String memberId,
            String name,
            String email,
            String birthDate
    ) {
        public static MeResponse from(Member member) {
            return new MeResponse(
                    member.getMemberId().getValue(),
                    member.getName().masked(),
                    member.getEmail().getValue(),
                    member.getBirthDate().getValue()
            );
        }
    }
}
