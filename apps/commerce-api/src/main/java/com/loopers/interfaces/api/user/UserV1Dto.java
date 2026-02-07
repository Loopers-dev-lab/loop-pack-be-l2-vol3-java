package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.Gender;

import java.time.LocalDate;

/** 사용자 API V1 요청/응답 DTO */
public class UserV1Dto {

    /** 이름은 개인정보 보호를 위해 maskedName으로 반환한다 */
    public record UserResponse(
            String loginId,
            String name,
            LocalDate birthDate,
            String email,
            Gender gender
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(info.loginId(), info.maskedName(), info.birthDate(), info.email(), info.gender());
        }
    }
}
