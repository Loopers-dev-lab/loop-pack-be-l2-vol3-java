package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;

import java.time.LocalDate;

/**
 * 사용자 정보 DTO (Application Layer)
 *
 * Entity를 외부 계층에 노출하지 않기 위한 변환용 DTO.
 * maskedName은 개인정보 보호를 위해 이름의 마지막 글자를 마스킹한 값이다.
 */
public record UserInfo(
        String loginId,
        String name,
        String maskedName,
        LocalDate birthDate,
        String email,
        Gender gender
) {
    public static UserInfo from(User user) {
        return new UserInfo(
                user.getLoginId().getValue(),
                user.getName().getValue(),
                user.getName().getMaskedValue(),
                user.getBirthDate().getValue(),
                user.getEmail().getValue(),
                user.getGender()
        );
    }
}
