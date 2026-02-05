package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;

// MemberInfo는 Facade → Controller로 전달되는 데이터
public record MemberInfo(String loginId, String name, String birthDate, String email) {
    public static MemberInfo from(MemberModel model) {
        return new MemberInfo(
            model.getLoginId(),
            model.getMaskedName(),
            model.getBirthDate(),
            model.getEmail()
          );
    }
}
