package com.loopers.interfaces.api.member;


import com.loopers.application.member.MemberInfo;

public class MemberV1Dto {

    // Request: POST방식으로 보낼때 데이터를 담는 그릇 (from 필요 없음)
    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email
    ) {}

    // Response: 변환 메서드(from)가 여기에!
    public record SignUpResponse(String loginId) {
      public static SignUpResponse from(MemberInfo info) {
        return new SignUpResponse(info.loginId());
      }
    }

    public record MemberInfoResponse(
        String loginId,
        String name,
        String birthDate,
        String email
    ) {
      public static MemberInfoResponse from(MemberInfo info) {
        return new MemberInfoResponse(
            info.loginId(),
            info.name(),
            info.birthDate(),
            info.email()
        );
      }
    }


}
